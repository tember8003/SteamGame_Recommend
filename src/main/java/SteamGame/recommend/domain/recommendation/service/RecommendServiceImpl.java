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
import SteamGame.recommend.infra.api.SteamApiService;
import SteamGame.recommend.domain.tag.service.TagService;
import SteamGame.recommend.infra.utils.EncryptUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
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

    public RecommendServiceImpl(
            SteamApiService steamApiService,
            GeminiApiService geminiApiService,
            TagService tagService,
            TagRepository tagRepository,
            CacheService cacheService,
            GameService gameService,
            GameRepository gameRepository,
            CooccurrenceService cooccurrenceService
    ) {
        this.steamApiService = steamApiService;
        this.geminiApiService = geminiApiService;
        this.tagService = tagService;
        this.tagRepository = tagRepository;
        this.gameRepository = gameRepository;
        this.cacheService = cacheService;
        this.gameService = gameService;
        this.cooccurrenceService = cooccurrenceService;
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

        // Cache check
        String shaInput = EncryptUtils.sha256(input);
        List<String> cachingTags = cacheService.getCachedTags(shaInput);
        if (cachingTags != null && !cachingTags.isEmpty()) {
            List<SteamAppDTO> game = gameService.findNonDuplicate(cachingTags.toArray(new String[0]), review, koreanCheck,freeCheck,false,null, excludeAppids);
            List<ReviewResponseDTO> reviewResponseDTOS = gameService.ResponseList(game);
            return new RecommendationResultDTO(cachingTags, game,reviewResponseDTOS);
        }

        boolean hit = cachingTags != null && !cachingTags.isEmpty();
        log.info("... Cache hit: {}", hit);

        List<String> tags = geminiApiService.getGeminiTags(input);
        if (tags.isEmpty()) {
            log.warn("Failed to extract tags. Using default tags instead.");
            tags = new ArrayList<>(FALLBACK_TAGS);
        }

        // Save to cache
        cacheService.cacheTags(shaInput,tags);

        // Final recommendation
        List<SteamAppDTO> game = gameService.findNonDuplicate(tags.toArray(new String[0]), review, koreanCheck,freeCheck,false,null, excludeAppids);

        List<ReviewResponseDTO> reviewResponseDTOS = gameService.ResponseList(game);
        return new RecommendationResultDTO(tags, game,reviewResponseDTOS);
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