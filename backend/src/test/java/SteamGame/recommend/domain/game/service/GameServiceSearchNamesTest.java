package SteamGame.recommend.domain.game.service;

import SteamGame.recommend.domain.game.dto.GameSearchResponseDTO;
import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.game.repository.ReviewRepository;
import SteamGame.recommend.domain.tag.service.TagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GameServiceSearchNamesTest {

    @Mock GameRepository gameRepository;
    @Mock TagService tagService;
    @Mock ReviewRepository reviewRepository;
    @InjectMocks GameService gameService;

    private Game named(String name) {
        Game g = new Game();
        g.setAppid(1L);
        g.setName(name);
        return g;
    }

    @Test
    void searchNames_preservesLatin1LetterAndQuestionMark() {
        // 구 GameService.cleanGameName 은 .replace("Â","") 로 Â 를 지웠음, 물음표는 손대지 않았음
        // 유물 제거로 이제 추천 경로(GameMapper)와 동일하게 원문을 보존함
        given(gameRepository.findByNameStartingWithIgnoreCase(anyString(), any()))
                .willReturn(List.of(named("Âbenteuer?!")));

        List<GameSearchResponseDTO> result = gameService.searchNames("A");

        assertThat(result).extracting(GameSearchResponseDTO::getGameName)
                .containsExactly("Âbenteuer?!");
    }

    @Test
    void searchNames_trimsSurroundingWhitespace() {
        given(gameRepository.findByNameStartingWithIgnoreCase(anyString(), any()))
                .willReturn(List.of(named("  Hollow Knight  ")));

        List<GameSearchResponseDTO> result = gameService.searchNames("H");

        assertThat(result).extracting(GameSearchResponseDTO::getGameName)
                .containsExactly("Hollow Knight");
    }
}
