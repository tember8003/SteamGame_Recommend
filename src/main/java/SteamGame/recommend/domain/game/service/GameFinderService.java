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
    public SteamDTO.SteamApp findNonDuplicate(String[] tags, int review, Boolean korean_check, Boolean free_check) {
        List<String> tagList = Arrays.asList(tags);
        for (int i = 0; i < 10; i++) {
            Optional<Game> optionalGame = gameRepository.findRandomGameByTags(tagList,tagList.size(),review,korean_check,free_check);

            if (optionalGame.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "조건에 맞는 게임을 찾을 수 없습니다.");
            }

            Game candidate = optionalGame.get();

            if (!cacheService.isAlreadyRecommended(candidate.getAppid())) {
                cacheService.setRecommended(candidate.getAppid());
                return GameMapper.convertToDTO(candidate);
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,"조건에 맞는 새로운 게임을 찾을 수 없습니다. (중복으로 인해 추천 실패)");
    }
}
