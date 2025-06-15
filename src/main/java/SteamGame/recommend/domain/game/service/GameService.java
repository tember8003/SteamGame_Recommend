package SteamGame.recommend.domain.game.service;

import SteamGame.recommend.domain.game.dto.GameSearchResponseDTO;
import SteamGame.recommend.domain.game.entity.Review;
import SteamGame.recommend.domain.game.repository.ReviewRepository;
import SteamGame.recommend.domain.recommendation.dto.ReviewResponseDTO;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.mapper.GameMapper;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.recommendation.service.CacheService;
import SteamGame.recommend.domain.tag.service.TagService;
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
    private final CacheService cacheService;
    private final TagService tagService;
    private final ReviewRepository reviewRepository;

    public GameService(GameRepository gameRepository,TagService tagService, CacheService cacheService, ReviewRepository reviewRepository){
        this.gameRepository = gameRepository;
        this.tagService = tagService;
        this.cacheService = cacheService;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public List<SteamAppDTO> findNonDuplicate(String[] tags,
                                                    int review,
                                                    Boolean korean_check,
                                                    Boolean free_check,
                                                    Boolean excluded_check,
                                                    String[] excludedTag) {
        List<SteamAppDTO> appList= new ArrayList<>();
        List<String> tagList = Arrays.asList(tags);

        List<String> excludedList = (excludedTag != null)
                ? Arrays.asList(excludedTag)
                : Collections.emptyList();

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
                if (!cacheService.isAlreadyRecommended(g.getAppid())) {
                    cacheService.setRecommended(g.getAppid());
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
            dto.setGameName(g.getName());
            dto.setHeaderImage(g.getImageUrl());
            gameName.add(dto);
        }

        return gameName;
    }
}
