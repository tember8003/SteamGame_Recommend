package SteamGame.recommend.domain.game.service;

import SteamGame.recommend.domain.game.dto.GameSearchResponseDTO;
import SteamGame.recommend.domain.recommendation.dto.SteamDTO;
import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.mapper.GameMapper;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.recommendation.service.CacheService;
import SteamGame.recommend.domain.tag.service.TagService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class GameService {
    private final GameRepository gameRepository;
    private final CacheService cacheService;
    private final TagService tagService;

    public GameService(GameRepository gameRepository,TagService tagService, CacheService cacheService){
        this.gameRepository = gameRepository;
        this.tagService = tagService;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public List<SteamDTO.SteamApp> findNonDuplicate(String[] tags,
                                                    int review,
                                                    Boolean korean_check,
                                                    Boolean free_check,
                                                    Boolean excluded_check,
                                                    String[] excludedTag) {
        List<SteamDTO.SteamApp> appList= new ArrayList<>();
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
                    appList.add(GameMapper.convertToDTO(g));
                    if (appList.size() == 4) {
                        break;
                    }
                }
            }
            count++;
        }

        if(appList.isEmpty()){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"조건에 맞는 새로운 게임을 찾을 수 없습니다.");
        }
        else{
            return appList;
        }
    }

    public SteamDTO.SteamApp findGameByAppid(long appid){
        Game game = gameRepository.findByAppid(appid).orElseThrow();

        if(game==null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게임이 존재하지 않습니다.");
        }

        SteamDTO.SteamApp steamApp = new SteamDTO.SteamApp();
        steamApp.setAppid(game.getAppid());
        steamApp.setName(game.getName());
        steamApp.setShortDescription(game.getDescription());
        steamApp.setHeaderImage(game.getImageUrl());
        steamApp.setSteamStore("");

        return steamApp;
    }

    public List<String> getTags(){
        return tagService.getFilteredTagNames();
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
