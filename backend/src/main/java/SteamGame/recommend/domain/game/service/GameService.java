package SteamGame.recommend.domain.game.service;

import SteamGame.recommend.domain.game.dto.GameSearchResponseDTO;
import SteamGame.recommend.domain.game.entity.Review;
import SteamGame.recommend.domain.game.repository.ReviewRepository;
import SteamGame.recommend.domain.recommendation.dto.ReviewResponseDTO;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.mapper.GameMapper;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.recommendation.MatchConstants;
import SteamGame.recommend.domain.recommendation.dto.RecommendationCriteria;
import SteamGame.recommend.domain.tag.service.TagService;
import SteamGame.recommend.infra.embedding.VectorLiterals;
import SteamGame.recommend.infra.embedding.VectorSampler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GameService {
    private final GameRepository gameRepository;
    private final TagService tagService;
    private final ReviewRepository reviewRepository;

    public GameService(GameRepository gameRepository, TagService tagService, ReviewRepository reviewRepository){
        this.gameRepository = gameRepository;
        this.tagService = tagService;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public List<SteamAppDTO> findNonDuplicate(String[] tags,
                                                    int review,
                                                    Boolean korean_check,
                                                    Boolean free_check,
                                                    Boolean excluded_check,
                                                    String[] excludedTag,
                                                    Set<Long> excludeAppids) {
        List<SteamAppDTO> appList= new ArrayList<>();
        List<String> tagList = Arrays.asList(tags);

        List<String> excludedList = (excludedTag != null)
                ? Arrays.asList(excludedTag)
                : Collections.emptyList();

        // 클라 주도 제외 + 호출 내부 로컬 dedup(전역 상태 없음)
        Set<Long> seen = (excludeAppids != null) ? new HashSet<>(excludeAppids) : new HashSet<>();

        int count = 0;

        while (appList.size() < 4 && count < 3) {
            List<Game> Games = gameRepository.findRandomGameByTags(tagList,
                    tagList.size(),
                    review,
                    korean_check,
                    free_check,
                    excluded_check,
                    excludedList);
            for (Game g : Games) {
                if (seen.add(g.getAppid())) {
                    appList.add(GameMapper.convertToSteamDTO(g));
                    if (appList.size() == 4) {
                        break;
                    }
                }
            }
            count++;
        }

        if(appList.isEmpty()){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No new games match your preferences");
        }
        else{
            return appList;
        }
    }

    // 임베딩 코사인 최근접 상위 poolK 후보를 뽑아 need 개를 균일 샘플. 임베딩된 후보가 없으면 빈 리스트(호출부가 폴백)
    @Transactional(readOnly = true)
    public List<SteamAppDTO> findNearestSampled(float[] qvec,
                                                int review,
                                                Boolean korean_check,
                                                Boolean free_check,
                                                boolean excluded_check,
                                                String[] excludedTag,
                                                Set<Long> excludeAppids,
                                                int poolK,
                                                int need) {
        String qLiteral = VectorLiterals.toPgVector(qvec);

        // 빈 집합이면 sentinel [-1] 바인딩 + excludeCount=0 으로 NOT IN 무력화
        List<Long> excludeList = (excludeAppids == null || excludeAppids.isEmpty())
                ? List.of(-1L)
                : new ArrayList<>(excludeAppids);
        int excludeCount = (excludeAppids != null) ? excludeAppids.size() : 0;

        List<String> excludedList = (excludedTag != null)
                ? Arrays.asList(excludedTag)
                : Collections.emptyList();

        List<Game> pool = gameRepository.findNearestByVector(qLiteral, review, korean_check, free_check,
                excludeList, excludeCount, excluded_check, excludedList, poolK);

        return VectorSampler.sample(pool, need, new Random()).stream()
                .map(GameMapper::convertToSteamDTO)
                .collect(Collectors.toList());
    }

    // 예산, 코옵 하드필터를 더한 임베딩 최근접 후보 poolK개를 그대로 반환, 샘플링은 후속 MatchService 가 담당함
    @Transactional(readOnly = true)
    public List<Game> matchNearestPool(float[] qvec, RecommendationCriteria c, int poolK) {
        String qLiteral = VectorLiterals.toPgVector(qvec);

        // 빈 집합이면 sentinel [-1] 바인딩 + excludeCount=0 으로 NOT IN 을 무력화한다
        List<Long> excludeList = (c.getExcludeAppids() == null || c.getExcludeAppids().isEmpty())
                ? List.of(-1L)
                : new ArrayList<>(c.getExcludeAppids());
        int excludeCount = (c.getExcludeAppids() != null) ? c.getExcludeAppids().size() : 0;

        boolean requireCoop = c.needsCoop();
        // 코옵이 필요 없으면 sentinel 태그로 IN() 이 비는 상황을 피함
        List<String> coopTags = requireCoop ? MatchConstants.COOP_TAGS : List.of("__none__");

        boolean excludedCheck = c.getExcludeTags() != null && !c.getExcludeTags().isEmpty();
        List<String> excludedTag = (c.getExcludeTags() != null) ? c.getExcludeTags() : Collections.emptyList();

        return gameRepository.findMatchNearest(qLiteral, c.getReview(), c.getKorean(), c.getFree(),
                c.getBudgetCents(), requireCoop, coopTags, excludeList, excludeCount,
                excludedCheck, excludedTag, poolK);
    }

    // 예산, 코옵 하드필터를 더한 품질(리뷰 수) 후보 poolK개를 그대로 반환, 임베딩이 없어도 후보에 포함됨
    @Transactional(readOnly = true)
    public List<Game> matchQualityPool(RecommendationCriteria c, int poolK) {
        List<Long> excludeList = (c.getExcludeAppids() == null || c.getExcludeAppids().isEmpty())
                ? List.of(-1L)
                : new ArrayList<>(c.getExcludeAppids());
        int excludeCount = (c.getExcludeAppids() != null) ? c.getExcludeAppids().size() : 0;

        boolean requireCoop = c.needsCoop();
        List<String> coopTags = requireCoop ? MatchConstants.COOP_TAGS : List.of("__none__");

        boolean excludedCheck = c.getExcludeTags() != null && !c.getExcludeTags().isEmpty();
        List<String> excludedTag = (c.getExcludeTags() != null) ? c.getExcludeTags() : Collections.emptyList();

        return gameRepository.findMatchByQuality(c.getReview(), c.getKorean(), c.getFree(),
                c.getBudgetCents(), requireCoop, coopTags, excludeList, excludeCount,
                excludedCheck, excludedTag, poolK);
    }

    public List<ReviewResponseDTO> ResponseList(List<SteamAppDTO> games){
        List<Long> appIds = games.stream()
                .map(SteamAppDTO::getAppid)
                .collect(Collectors.toList());

        log.info("Looking for reviews for appIds: {}", appIds);

        List<Review> reviews = reviewRepository.findByAppidIn(appIds);

        log.info("Found {} reviews for {} games", reviews.size(), games.size());

        Map<Long, Review> reviewMap = reviews.stream()
                .collect(Collectors.toMap(Review::getAppid, review -> review));

        List<ReviewResponseDTO> reviewResponseDTOS = new ArrayList<>();
        for(SteamAppDTO dto : games){
            Review review = reviewMap.get(dto.getAppid());
            if (review != null) {
                ReviewResponseDTO responseDTO = GameMapper.convertToReviewDTO(review);
                reviewResponseDTOS.add(responseDTO);
            } else {
                reviewResponseDTOS.add(new ReviewResponseDTO());
            }
        }

        return reviewResponseDTOS;
    }

    public SteamAppDTO findGameByAppid(long appid){
        Game game = gameRepository.findByAppid(appid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        return GameMapper.convertToSteamDTO(game);
    }

    public List<String> getTags(){
        return tagService.getTags();
    }

    public List<GameSearchResponseDTO> searchNames(String prefix) {
        Pageable limit5 = PageRequest.of(0, 5);
        List<Game> gameList = gameRepository.findByNameStartingWithIgnoreCase(prefix, limit5);

        List<GameSearchResponseDTO> gameName = new ArrayList<>();

        for(Game g : gameList){
            GameSearchResponseDTO dto = new GameSearchResponseDTO();
            dto.setGameName(cleanGameName(g.getName()));
            dto.setHeaderImage(g.getImageUrl());
            gameName.add(dto);
        }

        return gameName;
    }

    private static String cleanGameName(String name) {
        if (name == null) return null;

        return name
                .replace("짰","®")
                .replace("Â®", "®")
                .replace("â„¢", "™")
                .replace("Â©", "©")
                .replace("Â", "")
                .replace("â€™", "'")
                .replace("â€œ", "\"")
                .replace("â€", "\"")
                .trim();
    }
}
