package SteamGame.recommend.controller;

import SteamGame.recommend.domain.feedback.service.FeedbackSuppressionService;
import SteamGame.recommend.domain.game.dto.TextRequestDTO;
import SteamGame.recommend.domain.impression.service.ImpressionService;
import SteamGame.recommend.domain.recommendation.dto.RecommendationResultDTO;
import SteamGame.recommend.domain.recommendation.service.MatchService;
import SteamGame.recommend.domain.recommendation.service.RecommendService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendControllerSuppressionTest {

    RecommendService recommendService = mock(RecommendService.class);
    MatchService matchService = mock(MatchService.class);
    FeedbackSuppressionService suppressionService = mock(FeedbackSuppressionService.class);
    ImpressionService impressionService = mock(ImpressionService.class);
    RecommendController controller = new RecommendController(recommendService, matchService, suppressionService, impressionService);

    // 컨트롤러가 로그에서 getRecommendedGame().size(), getUsedTags() 를 부르므로 두 게터가 non-null 이어야 함
    // 스텁을 먼저 지역변수로 끝내 두고 thenReturn 에 넘김, thenReturn 인자 안에서 스텁하면 중첩 스텁이 됨
    private RecommendationResultDTO stubResult() {
        RecommendationResultDTO result = mock(RecommendationResultDTO.class);
        when(result.getRecommendedGame()).thenReturn(List.of());
        when(result.getUsedTags()).thenReturn(List.of());
        return result;
    }

    @Test
    void appliesSuppressedExcludesToService() {
        TextRequestDTO dto = new TextRequestDTO();
        dto.setText("코옵 게임");
        dto.setClientId("client-1");
        RecommendationResultDTO result = stubResult();
        when(suppressionService.resolveExcludes("client-1", null)).thenReturn(Set.of(111L));
        when(recommendService.selectInfo("코옵 게임", 0, null, null, Set.of(111L))).thenReturn(result);

        controller.inputRandomGame(dto);

        verify(recommendService).selectInfo("코옵 게임", 0, null, null, Set.of(111L));
    }

    @Test
    void retriesWithoutSuppressionWhenSuppressedQueryReturns404() {
        TextRequestDTO dto = new TextRequestDTO();
        dto.setText("코옵 게임");
        dto.setClientId("client-1");
        RecommendationResultDTO result = stubResult();
        when(suppressionService.resolveExcludes("client-1", null)).thenReturn(Set.of(111L));
        when(recommendService.selectInfo("코옵 게임", 0, null, null, Set.of(111L)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No new games"));
        when(recommendService.selectInfo("코옵 게임", 0, null, null, null)).thenReturn(result);

        controller.inputRandomGame(dto);

        verify(recommendService).selectInfo("코옵 게임", 0, null, null, Set.of(111L));
        verify(recommendService).selectInfo("코옵 게임", 0, null, null, null);
    }

    @Test
    void noClientId_passesNormalizedExcludes_withoutRetry() {
        TextRequestDTO dto = new TextRequestDTO();
        dto.setText("코옵 게임");
        RecommendationResultDTO result = stubResult();
        when(suppressionService.resolveExcludes(null, null)).thenReturn(Set.of());
        when(recommendService.selectInfo("코옵 게임", 0, null, null, Set.of())).thenReturn(result);

        controller.inputRandomGame(dto);

        verify(recommendService).selectInfo("코옵 게임", 0, null, null, Set.of());
    }
}
