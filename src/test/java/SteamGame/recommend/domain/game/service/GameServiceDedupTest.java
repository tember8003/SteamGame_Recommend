package SteamGame.recommend.domain.game.service;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.game.repository.ReviewRepository;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.tag.service.TagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GameServiceDedupTest {

    @Mock GameRepository gameRepository;
    @Mock TagService tagService;
    @Mock ReviewRepository reviewRepository;
    @InjectMocks GameService gameService;

    private Game game(long appid) {
        Game g = new Game();
        g.setAppid(appid);
        g.setName("game-" + appid);
        return g;
    }

    @Test
    void excludeAppids_skipsRequestedApps() {
        given(gameRepository.findRandomGameByTags(any(), anyLong(), anyInt(), any(), any(), any(), any()))
                .willReturn(List.of(game(1), game(2), game(3), game(4)));

        List<SteamAppDTO> result = gameService.findNonDuplicate(
                new String[]{"RPG"}, 0, false, false, false, null, Set.of(2L, 3L));

        assertThat(result).extracting(SteamAppDTO::getAppid)
                .containsExactlyInAnyOrder(1L, 4L);
    }

    @Test
    void noGlobalSuppression_repeatedCallsCanReturnSameApp() {
        given(gameRepository.findRandomGameByTags(any(), anyLong(), anyInt(), any(), any(), any(), any()))
                .willReturn(List.of(game(1), game(2), game(3), game(4)));

        List<SteamAppDTO> first = gameService.findNonDuplicate(
                new String[]{"RPG"}, 0, false, false, false, null, Set.of());
        List<SteamAppDTO> second = gameService.findNonDuplicate(
                new String[]{"RPG"}, 0, false, false, false, null, Set.of());

        // 전역 억제가 사라져 두 호출이 같은 appid 를 반환할 수 있음
        assertThat(first).extracting(SteamAppDTO::getAppid).contains(1L);
        assertThat(second).extracting(SteamAppDTO::getAppid).contains(1L);
    }

    @Test
    void nullExcludeAppids_isTreatedAsEmpty() {
        given(gameRepository.findRandomGameByTags(any(), anyLong(), anyInt(), any(), any(), any(), any()))
                .willReturn(List.of(game(1), game(2), game(3), game(4)));

        List<SteamAppDTO> result = gameService.findNonDuplicate(
                new String[]{"RPG"}, 0, false, false, false, null, null);

        assertThat(result).hasSize(4);
    }
}
