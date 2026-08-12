package SteamGame.recommend.domain.recommendation.service;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.game.service.GameService;
import SteamGame.recommend.domain.recommendation.dto.RecommendationResultDTO;
import SteamGame.recommend.domain.recommendation.dto.ReviewResponseDTO;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.recommendation.entity.TagPairKey;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import SteamGame.recommend.infra.api.GeminiApiService;
import SteamGame.recommend.infra.api.GeminiEmbeddingService;
import SteamGame.recommend.infra.api.SteamApiService;
import SteamGame.recommend.domain.tag.service.TagService;
import SteamGame.recommend.infra.embedding.EmbeddingProperties;
import SteamGame.recommend.infra.embedding.EmbeddingRepository;
import SteamGame.recommend.infra.embedding.VectorLiterals;
import SteamGame.recommend.infra.utils.EncryptUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RecommendServiceImpl implements RecommendService {

    private static final int CO_THRESHOLD = 5;
    private static final List<String> FALLBACK_TAGS = List.of("Singleplayer","Multiplayer");

    private static final int MAX_RECOMMENDATION_ATTEMPTS = 5;
    private static final int MAX_SEARCH_ITERATIONS = 3;
    private static final int TOP_TAGS_COUNT = 8;

    private final SteamApiService steamApiService;
    private final GeminiApiService geminiApiService;
    private final TagService tagService;
    private final TagRepository tagRepository;
    private final CacheService cacheService;
    private final GameService gameService;
    private final GameRepository gameRepository;
    private final CooccurrenceService cooccurrenceService;
    private final GeminiEmbeddingService geminiEmbeddingService;
    private final EmbeddingRepository embeddingRepository;
    private final EmbeddingProperties embeddingProperties;

    public RecommendServiceImpl(
            SteamApiService steamApiService,
            GeminiApiService geminiApiService,
            TagService tagService,
            TagRepository tagRepository,
            CacheService cacheService,
            GameService gameService,
            GameRepository gameRepository,
            CooccurrenceService cooccurrenceService,
            GeminiEmbeddingService geminiEmbeddingService,
            EmbeddingRepository embeddingRepository,
            EmbeddingProperties embeddingProperties
    ) {
        this.steamApiService = steamApiService;
        this.geminiApiService = geminiApiService;
        this.tagService = tagService;
        this.tagRepository = tagRepository;
        this.gameRepository = gameRepository;
        this.cacheService = cacheService;
        this.gameService = gameService;
        this.cooccurrenceService = cooccurrenceService;
        this.geminiEmbeddingService = geminiEmbeddingService;
        this.embeddingRepository = embeddingRepository;
        this.embeddingProperties = embeddingProperties;
    }

    // 벡터 결과가 목표 수(resultSize)보다 적으면 태그 폴백으로 슬롯을 채운다.
    // filler 는 이미 뽑힌 appid 집합을 받아 그 밖의 후보를 반환(폴백이 비면 예외 → 벡터 결과 유지)
    private List<SteamAppDTO> topUp(List<SteamAppDTO> ranked,
                                    Function<Set<Long>, List<SteamAppDTO>> filler) {
        int need = embeddingProperties.getResultSize();
        if (ranked.size() >= need) {
            return ranked;
        }
        Set<Long> picked = new HashSet<>();
        ranked.forEach(dto -> picked.add(dto.getAppid()));
        try {
            List<SteamAppDTO> extra = filler.apply(picked);
            List<SteamAppDTO> merged = new ArrayList<>(ranked);
            for (SteamAppDTO f : extra) {
                if (merged.size() >= need) {
                    break;
                }
                if (picked.add(f.getAppid())) {
                    merged.add(f);
                }
            }
            return merged;
        } catch (ResponseStatusException e) {
            return ranked;
        }
    }

    // Find games based on tags, reviews, Korean support, free status, etc.
    @Override
    @Transactional(readOnly=true)
    public RecommendationResultDTO findGame(String[] tags, int review, Boolean koreanCheck, Boolean freeCheck, Boolean excluded_check, String[] excludedTag, Set<Long> excludeAppids) {
        List<SteamAppDTO> steamAppDTOS= gameService.findNonDuplicate(tags,review,koreanCheck,freeCheck,excluded_check,excludedTag, excludeAppids);
        if(steamAppDTOS.isEmpty()){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No games found matching the specified tags."
            );
        }

        List<ReviewResponseDTO> reviewResponseDTOS = gameService.ResponseList(steamAppDTOS);
        return new RecommendationResultDTO(Arrays.asList(tags), steamAppDTOS,reviewResponseDTOS);
    }

    // Extract game tags using Gemini API and find games
    @Override
    public RecommendationResultDTO selectInfo(String input, int review, Boolean koreanCheck, Boolean freeCheck, Set<Long> excludeAppids) {
        if (input == null || input.length() < MAX_SEARCH_ITERATIONS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The input sentence is too short.");
        }

        // usedTags 표시용 태그 확보 (캐시 우선, 없으면 Gemini 추출)
        String shaInput = EncryptUtils.sha256(input);
        List<String> tags = cacheService.getCachedTags(shaInput);
        if (tags == null || tags.isEmpty()) {
            tags = geminiApiService.getGeminiTags(input);
            if (tags.isEmpty()) {
                log.warn("Failed to extract tags. Using default tags instead.");
                tags = new ArrayList<>(FALLBACK_TAGS);
            }
            cacheService.cacheTags(shaInput, tags);
        }

        // 벡터 랭킹 우선: 입력 문장을 직접 임베딩(태그 추출 손실 우회), 재추천 대비 캐시
        final List<String> finalTags = tags;
        float[] qvec;
        String cachedLit = cacheService.getCachedEmbedding(shaInput);
        if (cachedLit != null) {
            qvec = VectorLiterals.parse(cachedLit);
        } else {
            qvec = geminiEmbeddingService.embed(input);
            if (qvec != null) {
                cacheService.cacheEmbedding(shaInput, VectorLiterals.toPgVector(qvec));
            }
        }
        if (qvec != null) {
            List<SteamAppDTO> ranked = gameService.findNearestSampled(qvec, review, koreanCheck, freeCheck,
                    false, null, excludeAppids,
                    embeddingProperties.getCandidatePoolK(), embeddingProperties.getResultSize());
            if (!ranked.isEmpty()) {
                ranked = topUp(ranked, picked -> {
                    Set<Long> ex = new HashSet<>(picked);
                    if (excludeAppids != null) ex.addAll(excludeAppids);
                    return gameService.findNonDuplicate(finalTags.toArray(new String[0]),
                            review, koreanCheck, freeCheck, false, null, ex);
                });
                return new RecommendationResultDTO(tags, ranked, gameService.ResponseList(ranked));
            }
        }

        // 폴백: 태그 매칭
        List<SteamAppDTO> game = gameService.findNonDuplicate(tags.toArray(new String[0]), review, koreanCheck, freeCheck, false, null, excludeAppids);
        return new RecommendationResultDTO(tags, game, gameService.ResponseList(game));
    }

    // Extract tags from a Steam user profile games list
    @Override
    public RecommendationResultDTO recommendByProfile(String steamId,int review, Boolean koreanCheck, Boolean freeCheck, Set<Long> excludeAppids){
        Set<Long> ownedAppIds  = new HashSet<>(steamApiService.getOwnedGameIds(steamId));
        List<String> topTags = tagService.getTopTagsByProfile(steamId,TOP_TAGS_COUNT);

        if(topTags.isEmpty()){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No played games found or no tags could be extracted."
            );
        }

        // 벡터 우선: 보유작 평균 taste 벡터로 최근접 추천(보유작 제외)
        float[] taste = embeddingRepository.findMeanEmbeddingByAppids(new ArrayList<>(ownedAppIds));
        if (taste != null) {
            // 보유작에 더해 클라이언트 제외, 억제(DOWN)까지 함께 뺀다
            Set<Long> exclude = new HashSet<>(ownedAppIds);
            if (excludeAppids != null) exclude.addAll(excludeAppids);
            List<SteamAppDTO> ranked = gameService.findNearestSampled(taste, review, koreanCheck, freeCheck,
                    false, null, exclude,
                    embeddingProperties.getCandidatePoolK(), embeddingProperties.getResultSize());
            if (!ranked.isEmpty()) {
                ranked = topUp(ranked, picked -> {
                    Set<Long> ex = new HashSet<>(picked);
                    ex.addAll(exclude);
                    return recommendWithCooccurrence(topTags, review, koreanCheck, freeCheck, ex);
                });
                return new RecommendationResultDTO(topTags, ranked, gameService.ResponseList(ranked));
            }
        }

        /* Top 2 tags + 2 random tags logics applied.
        List<String> selectedTags = new ArrayList<>();
        selectedTags.add(topTags.get(0));
        if (topTags.size() > 1) {
            selectedTags.add(topTags.get(1));
        }

        List<String> rest = new ArrayList<>();
        if (topTags.size() > 2) {
            rest.addAll(topTags.subList(2, topTags.size()));
            Collections.shuffle(rest);
            for (int i = 0; i < 2 && i < rest.size(); i++) {
                selectedTags.add(rest.get(i));
            }
        }
         */
        List<String> tags = null;
        List<SteamAppDTO> resultGames = null;

        for (int attempt = 0; attempt < MAX_RECOMMENDATION_ATTEMPTS; attempt++) {
            tags = tagService.shuffleTag(topTags,5,7);
            List<SteamAppDTO> games = recommendWithCooccurrence(tags,review,koreanCheck,freeCheck, excludeAppids);

            List<SteamAppDTO> filteredGames = games.stream()
                    .filter(app -> !ownedAppIds.contains(app.getAppid()))
                    .collect(Collectors.toList());

            if(!filteredGames.isEmpty()){
                resultGames = filteredGames;
                break;
            }
        }

        if (resultGames == null || resultGames.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "You already own all games matching the recommended tags."
            );
        }

        List<ReviewResponseDTO> reviewResponseDTOS = gameService.ResponseList(resultGames);
        return new RecommendationResultDTO(tags,resultGames,reviewResponseDTOS);
    }

    // Find games based on tags extracted from a user profile
    @Override
    public List<SteamAppDTO> recommendWithCooccurrence(List<String> topTags,int review, Boolean koreanCheck, Boolean freeCheck, Set<Long> excludeAppids) {
        Optional<TagPairKey> optKey = cooccurrenceService.findOptimalPairKey(topTags, CO_THRESHOLD);

        if (optKey.isPresent()) {
            TagPairKey key = optKey.get();
            return gameService.findNonDuplicate(
                    new String[]{ key.getFirstTag(), key.getSecondTag() },
                    review,
                    koreanCheck,
                    freeCheck,
                    false,
                    null,
                    excludeAppids
            );
        }

        for (String tag : topTags) {
            try {
                return gameService.findNonDuplicate(
                        new String[]{tag},
                        review,
                        koreanCheck,
                        freeCheck,
                        false,
                        null,
                        excludeAppids);
            } catch (ResponseStatusException ignored) { }
        }

        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No games available for recommendation."
        );
    }

    // Extract tags from recently played (2 weeks) games and find games
    @Override
    @Transactional(readOnly = true)
    public RecommendationResultDTO recommendByRecentPlay(String steamId,int review, Boolean koreanCheck, Boolean freeCheck, Set<Long> excludeAppids) {
        List<Long> recentAppIds = steamApiService.getRecentPlayedGameIds(steamId);
        Set<Long> ownedAppIds  = new HashSet<>(steamApiService.getOwnedGameIds(steamId));

        if (recentAppIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No recently played games found.");
        }

        List<String> allTags = tagRepository.findTagNamesByAppIds(recentAppIds);

        List<String> topTags = tagService.getTopTags(allTags,TOP_TAGS_COUNT);

        // 벡터 우선: 최근플레이 평균 taste 벡터로 최근접 추천(보유작 제외)
        float[] taste = embeddingRepository.findMeanEmbeddingByAppids(recentAppIds);
        if (taste != null) {
            // 보유작에 더해 클라이언트 제외, 억제(DOWN)까지 함께 뺀다
            Set<Long> exclude = new HashSet<>(ownedAppIds);
            if (excludeAppids != null) exclude.addAll(excludeAppids);
            List<SteamAppDTO> ranked = gameService.findNearestSampled(taste, review, koreanCheck, freeCheck,
                    false, null, exclude,
                    embeddingProperties.getCandidatePoolK(), embeddingProperties.getResultSize());
            if (!ranked.isEmpty()) {
                ranked = topUp(ranked, picked -> {
                    Set<Long> ex = new HashSet<>(picked);
                    ex.addAll(exclude);
                    return recommendWithCooccurrence(topTags, review, koreanCheck, freeCheck, ex);
                });
                return new RecommendationResultDTO(topTags, ranked, gameService.ResponseList(ranked));
            }
        }

        List<String> tags = null;
        List<SteamAppDTO> resultGames = null;

        for (int attempt = 0; attempt < 5; attempt++) {
            tags = tagService.shuffleTag(topTags,4,7);
            List<SteamAppDTO> games = recommendWithCooccurrence(tags,review,koreanCheck,freeCheck, excludeAppids);

            List<SteamAppDTO> filteredGames = games.stream()
                    .filter(app -> !ownedAppIds.contains(app.getAppid()))
                    .collect(Collectors.toList());

            if(!filteredGames.isEmpty()){
                resultGames = filteredGames;
                break;
            }
        }

        if (resultGames == null || resultGames.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "You already own all games matching the recommended tags."
            );
        }

        List<ReviewResponseDTO> reviewResponseDTOS = gameService.ResponseList(resultGames);
        return new RecommendationResultDTO(tags,resultGames,reviewResponseDTOS);
    }

    @Override
    public RecommendationResultDTO recommendBySimilarGame(String gameName,int review, Boolean koreanCheck, Boolean freeCheck, Set<Long> excludeAppids){
        List<String> selectedTags = tagService.getTagsByGame(gameName);
        Optional<Game> selectedGame = gameRepository.findByNameIgnoreCase(gameName);
        long selectedAppId = selectedGame.orElseThrow().getAppid();


        if (selectedTags.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "The game has no tags or tag information could not be found."
            );
        }

        log.info(selectedTags.toString());

        List<String> topTags = tagService.getTopTags(selectedTags,TOP_TAGS_COUNT);

        // 벡터 우선: 시드 게임 임베딩으로 최근접 추천(시드 제외)
        float[] seedVec = embeddingRepository.findEmbeddingByAppid(selectedAppId);
        if (seedVec != null) {
            Set<Long> exclude = (excludeAppids != null) ? new HashSet<>(excludeAppids) : new HashSet<>();
            exclude.add(selectedAppId);
            List<SteamAppDTO> ranked = gameService.findNearestSampled(seedVec, review, koreanCheck, freeCheck,
                    false, null, exclude,
                    embeddingProperties.getCandidatePoolK(), embeddingProperties.getResultSize());
            if (!ranked.isEmpty()) {
                ranked = topUp(ranked, picked -> {
                    Set<Long> ex = new HashSet<>(picked);
                    ex.addAll(exclude);
                    return recommendWithCooccurrence(topTags, review, koreanCheck, freeCheck, ex);
                });
                return new RecommendationResultDTO(topTags, ranked, gameService.ResponseList(ranked));
            }
        }

        List<SteamAppDTO> resultGames = null;

        for (int attempt = 0; attempt < 5; attempt++) {
            List<SteamAppDTO> games =recommendWithCooccurrence(topTags, review, koreanCheck, freeCheck, excludeAppids);

            List<SteamAppDTO> filteredGames = games.stream()
                    .filter(app -> !app.getAppid().equals(selectedAppId))
                    .collect(Collectors.toList());

            if(!filteredGames.isEmpty()){
                resultGames = filteredGames;
                break;
            }
        }

        if (resultGames == null || resultGames.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Failed to find games with similar tags to the selected game."
            );
        }

        List<ReviewResponseDTO> reviewResponseDTOS = gameService.ResponseList(resultGames);
        return new RecommendationResultDTO(topTags, resultGames,reviewResponseDTOS);
    }

}