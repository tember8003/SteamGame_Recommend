package SteamGame.recommend.domain.choice.service;

import SteamGame.recommend.domain.choice.dto.ChoiceResultDTO;
import SteamGame.recommend.domain.choice.dto.ChoiceSession;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.game.service.GameService;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.recommendation.service.CooccurrenceService;
import SteamGame.recommend.domain.recommendation.service.InMemoryCacheService;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameChoiceServiceTest {

    @Mock GameRepository gameRepository;
    @Mock TagRepository tagRepository;
    @Mock CooccurrenceService cooccurrenceService;
    @Mock GameService gameService;
    @Mock InMemoryCacheService cacheService;
    @InjectMocks GameChoiceService gameChoiceService;

    private SteamAppDTO game(long appid) {
        SteamAppDTO dto = new SteamAppDTO();
        dto.setAppid(appid);
        dto.setName("game-" + appid);
        return dto;
    }

    // 세션의 선택 게임(excludedGameIds)이 GameService.findNonDuplicate 의 excludeAppids 로 실제 전달되는지 검증
    @Test
    void getChoiceResult_passesSessionSelectionsAsExcludeAppids() {
        String sessionId = "session-1";
        List<Long> selections = List.of(101L, 202L);
        ChoiceSession session = new ChoiceSession(sessionId, List.of(), selections, true);

        given(cacheService.getObject("choice:" + sessionId, ChoiceSession.class))
                .willReturn(Optional.of(session));
        given(tagRepository.findTagNamesByAppIds(any()))
                .willReturn(List.of("RPG", "Action", "Indie"));
        given(cooccurrenceService.findOptimalPairKey(any(), anyInt()))
                .willReturn(Optional.empty());
        given(gameService.findNonDuplicate(any(), anyInt(), any(), any(), any(), any(), any()))
                .willReturn(List.of(game(999L)));

        ChoiceResultDTO result = gameChoiceService.getChoiceResult(sessionId, 0, false, false);

        assertThat(result.getRecommendedGames()).extracting(SteamAppDTO::getAppid).contains(999L);

        ArgumentCaptor<Set<Long>> excludeAppidsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(gameService).findNonDuplicate(any(), anyInt(), any(), any(), any(), any(), excludeAppidsCaptor.capture());

        assertThat(excludeAppidsCaptor.getValue()).containsExactlyInAnyOrder(101L, 202L);
    }
}
