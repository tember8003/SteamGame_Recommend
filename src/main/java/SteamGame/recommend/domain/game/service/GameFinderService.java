package SteamGame.recommend.domain.game.service;

import SteamGame.recommend.domain.recommendation.dto.SteamDTO;
import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.mapper.GameMapper;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.recommendation.service.CacheService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class GameFinderService {
    private final GameRepository gameRepository;
    private final CacheService cacheService;

    public GameFinderService(GameRepository gameRepository, CacheService cacheService){
        this.gameRepository = gameRepository;

        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public List<SteamDTO.SteamApp> findNonDuplicate(String[] tags, int review, Boolean korean_check, Boolean free_check) {
        List<SteamDTO.SteamApp> appList= new ArrayList<>();
        List<String> tagList = Arrays.asList(tags);

        int count = 0;

        while (appList.size() < 4 && count < 3) {
            List<Game> Games = gameRepository.findRandomGameByTags(tagList,tagList.size(),review,korean_check,free_check);
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

    public Game findGameByAppid(long appid){
        Game game = gameRepository.findByAppid(appid).orElseThrow();

        if(game==null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게임이 존재하지 않습니다.");
        }

        return game;
    }
}
