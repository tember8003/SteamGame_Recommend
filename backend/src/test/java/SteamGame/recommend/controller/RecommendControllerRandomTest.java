package SteamGame.recommend.controller;

import SteamGame.recommend.domain.feedback.service.FeedbackSuppressionService;
import SteamGame.recommend.domain.game.dto.GameRequestDTO;
import SteamGame.recommend.domain.impression.service.ImpressionService;
import SteamGame.recommend.domain.recommendation.dto.RecommendationResultDTO;
import SteamGame.recommend.domain.recommendation.service.MatchService;
import SteamGame.recommend.domain.recommendation.service.RecommendService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendControllerRandomTest {

    RecommendService recommendService = mock(RecommendService.class);
    MatchService matchService = mock(MatchService.class);
    FeedbackSuppressionService suppressionService = mock(FeedbackSuppressionService.class);
    ImpressionService impressionService = mock(ImpressionService.class);
    RecommendController controller = new RecommendController(recommendService, matchService, suppressionService, impressionService);

    private RecommendationResultDTO stubResult() {
        RecommendationResultDTO result = mock(RecommendationResultDTO.class);
        when(result.getRecommendedGame()).thenReturn(List.of());
        when(result.getUsedTags()).thenReturn(List.of());
        return result;
    }

    @Test
    void randomGame_withoutTags_passesEmptyArrayInsteadOfNull() {
        GameRequestDTO dto = new GameRequestDTO();
        dto.setReview(1000);
        RecommendationResultDTO result = stubResult();
        when(recommendService.findGame(any(), anyInt(), any(), any(), any(), any(), any())).thenReturn(result);

        var response = controller.randomGame(dto);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(recommendService).findGame(argThat(tags -> tags != null && tags.length == 0),
                eq(1000), any(), any(), any(), any(), any());
    }
}
