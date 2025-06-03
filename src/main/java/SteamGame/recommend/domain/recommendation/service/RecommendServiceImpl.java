package SteamGame.recommend.domain.recommendation.service;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.service.GameFinderService;
import SteamGame.recommend.domain.recommendation.dto.SteamDTO;
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
    private static final int DEFAULT_REVIEW = 1000;
    private static final List<String> FALLBACK_TAGS = List.of("싱글 플레이어","멀티플레이어");

    private final SteamApiService steamApiService;
    private final GeminiApiService geminiApiService;
    private final TagService tagService;
    private final TagRepository tagRepository;
    private final CacheService cacheService;
    private final GameFinderService gameFinderService;
    private final CooccurrenceService cooccurrenceService;

    public RecommendServiceImpl(
            SteamApiService steamApiService,
            GeminiApiService geminiApiService,
            TagService tagService,
            TagRepository tagRepository,
            CacheService cacheService,
            GameFinderService gameFinderService,
            CooccurrenceService cooccurrenceService
    ) {
        this.steamApiService = steamApiService;
        this.geminiApiService = geminiApiService;
        this.tagService = tagService;
        this.tagRepository = tagRepository;
        this.cacheService = cacheService;
        this.gameFinderService = gameFinderService;
        this.cooccurrenceService = cooccurrenceService;
    }

    //태그, 리뷰, 한글화, 무료여부 등 조건으로 게임 찾기.
    @Override
    @Transactional(readOnly=true)
    public List<SteamDTO.SteamApp> findGame(String[] tags, int review, Boolean koreanCheck, Boolean freeCheck,Boolean excluded_check,String[] excludedTag) {
        return gameFinderService.findNonDuplicate(tags,review,koreanCheck,freeCheck,excluded_check,excludedTag);
    }

    //Gemini API를 활용해 게임 태그 추출해 게임 찾기
    @Override
    public SteamDTO.RecommendationResult selectInfo(String input,int review, Boolean koreanCheck, Boolean freeCheck) {
        if (input == null || input.length() < 3) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "입력 문장이 너무 짧습니다.");
        }

        //캐시 검사
        String shaInput = EncryptUtils.sha256(input);
        List<String> cachingTags = cacheService.getCachedTags(shaInput);
        if (cachingTags != null && !cachingTags.isEmpty()) {
            List<SteamDTO.SteamApp> game = findGame(cachingTags.toArray(new String[0]), review, koreanCheck,freeCheck,false,null);
            return new SteamDTO.RecommendationResult(cachingTags, game);
        }

        boolean hit = cachingTags != null && !cachingTags.isEmpty();
        log.info("… 캐시 히트: {}", hit);

        List<String> tags = geminiApiService.getGeminiTags(input);
        if (tags.isEmpty()) {
            log.warn("태그 추출 실패, 기본 태그로 대체");
            tags = new ArrayList<>(FALLBACK_TAGS);
        }

        // 캐시에 저장
        cacheService.cacheTags(shaInput,tags);

        // 최종 추천
        List<SteamDTO.SteamApp> game = findGame(tags.toArray(new String[0]), review, koreanCheck,freeCheck,false,null);
        return new SteamDTO.RecommendationResult(tags, game);
    }

    //스팀 사용자 프로필에 있는 게임들 리스트를 받아와 태그 뽑아내기
    @Override
    public SteamDTO.RecommendationResult recommendByProfile(String steamId,int review, Boolean koreanCheck, Boolean freeCheck){
        Set<Long> ownedAppIds  = new HashSet<>(steamApiService.getOwnedGameIds(steamId));
        List<String> topTags = tagService.getTopTagsByProfile(steamId,8);

        if(topTags.isEmpty()){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "플레이한 게임이 없거나 태그를 찾을 수 없습니다."
            );
        }

        /* 상위 태그 2개 - 랜덤 태그 2개 로직
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
        List<SteamDTO.SteamApp> resultGames = null;

        for (int attempt = 0; attempt < 5; attempt++) {
            tags = tagService.shuffleTag(topTags,5,7);
            List<SteamDTO.SteamApp> games = recommendWithCooccurrence(tags,review,koreanCheck,freeCheck);

            List<SteamDTO.SteamApp> filteredGames = games.stream()
                    .filter(app -> !ownedAppIds.contains(app.getAppid()))
                    .collect(Collectors.toList());

            if(!filteredGames.isEmpty()){
                resultGames = filteredGames;
                break;
            }
        }

        if (resultGames == null || resultGames.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "추천된 태그 내 게임들을 모두 소유했습니다."
            );
        }

        return new SteamDTO.RecommendationResult(tags,resultGames);
    }

    //사용자 프로필에서 뽑아낸 태그들을 바탕으로 게임 찾기
    @Override
    public List<SteamDTO.SteamApp> recommendWithCooccurrence(List<String> topTags,int review, Boolean koreanCheck, Boolean freeCheck) {
        Optional<TagPairKey> optKey = cooccurrenceService.findOptimalPairKey(topTags, CO_THRESHOLD);

        if (optKey.isPresent()) {
            TagPairKey key = optKey.get();
            return findGame(
                    new String[]{ key.getFirstTag(), key.getSecondTag() },
                    review,
                    koreanCheck,
                    freeCheck,
                    false,
                    null
            );
        }

        for (String tag : topTags) {
            try {
                return findGame(
                        new String[]{tag},
                        review,
                        koreanCheck,
                        freeCheck,
                        false,
                        null);
            } catch (ResponseStatusException ignored) { }
        }

        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "추천 가능한 게임이 없습니다."
        );
    }

    //최근 플레이(2주) 게임 태그들을 뽑아내 게임 찾기
    @Override
    @Transactional(readOnly = true)
    public SteamDTO.RecommendationResult recommendByRecentPlay(String steamId,int review, Boolean koreanCheck, Boolean freeCheck) {
        List<Long> recentAppIds = steamApiService.getRecentPlayedGameIds(steamId);
        Set<Long> ownedAppIds  = new HashSet<>(steamApiService.getOwnedGameIds(steamId));

        if (recentAppIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "최근 플레이한 게임이 없습니다.");
        }

        List<String> allTags = tagRepository.findTagNamesByAppIds(recentAppIds);

        List<String> topTags = tagService.getTopTags(allTags,8);

        List<String> tags = null;
        List<SteamDTO.SteamApp> resultGames = null;

        for (int attempt = 0; attempt < 5; attempt++) {
            tags = tagService.shuffleTag(topTags,4,7);
            List<SteamDTO.SteamApp> games = recommendWithCooccurrence(tags,review,koreanCheck,freeCheck);

            List<SteamDTO.SteamApp> filteredGames = games.stream()
                    .filter(app -> !ownedAppIds.contains(app.getAppid()))
                    .collect(Collectors.toList());

            if(!filteredGames.isEmpty()){
                resultGames = filteredGames;
                break;
            }
        }

        if (resultGames == null || resultGames.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "추천된 태그 내 게임들을 모두 소유했습니다."
            );
        }

        return new SteamDTO.RecommendationResult(tags,resultGames);
    }

    @Override
    public SteamDTO.RecommendationResult recommendBySimilarGame(String gameName,int review, Boolean koreanCheck, Boolean freeCheck){
        List<String> selectedTags = tagService.getTagsByGame(gameName);

        if (selectedTags.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "태그가 없는 게임이거나, 태그 정보를 찾을 수 없습니다."
            );
        }

        log.info(selectedTags.toString());

        List<String> topTags = tagService.getTopTags(selectedTags,8);

        List<SteamDTO.SteamApp> game = recommendWithCooccurrence(topTags,review,koreanCheck,freeCheck);

        return new SteamDTO.RecommendationResult(topTags, game);
    }

    //전체 태그 반환
    @Override
    public List<String> getTags(){
        return tagService.getFilteredTagNames();
    }

    //게임 아이디로 게임 찾기
    @Override
    public SteamDTO.SteamApp findGameByAppid(long appid){
        Game game = gameFinderService.findGameByAppid(appid);

        SteamDTO.SteamApp steamApp = new SteamDTO.SteamApp();
        steamApp.setAppid(game.getAppid());
        steamApp.setName(game.getName());
        steamApp.setShortDescription(game.getDescription());
        steamApp.setHeaderImage(game.getImageUrl());
        steamApp.setSteamStore("");

        return steamApp;
    }
}
