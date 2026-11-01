package SteamGame.recommend.domain.metrics.service;

import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.feedback.entity.FeedbackSignal;
import SteamGame.recommend.domain.feedback.repository.RecommendationFeedbackRepository;
import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.impression.repository.RecommendationImpressionRepository;
import SteamGame.recommend.domain.metrics.dto.MetricsOverviewDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock RecommendationImpressionRepository impressionRepository;
    @Mock RecommendationFeedbackRepository feedbackRepository;
    @Mock GameRepository gameRepository;
    @InjectMocks MetricsService service;

    // 프로젝션 인터페이스를 구현하는 테스트용 레코드
    private record ImpCtx(FeedbackContext ctx, long n) implements RecommendationImpressionRepository.ContextCount {
        public FeedbackContext getContext() { return ctx; }
        public long getCount() { return n; }
    }
    private record ImpApp(Long id, long n) implements RecommendationImpressionRepository.AppidCount {
        public Long getAppid() { return id; }
        public long getCount() { return n; }
    }
    private record FbRow(FeedbackContext ctx, FeedbackSignal sig, long n) implements RecommendationFeedbackRepository.ContextSignalCount {
        public FeedbackContext getContext() { return ctx; }
        public FeedbackSignal getSignal() { return sig; }
        public long getCount() { return n; }
    }

    @Test
    void overview_emptyData_returnsZeroForEveryContext() {
        when(impressionRepository.count()).thenReturn(0L);
        when(impressionRepository.countByContext()).thenReturn(List.of());
        when(feedbackRepository.countByContextAndSignal()).thenReturn(List.of());
        when(impressionRepository.topAppids(any())).thenReturn(List.of());

        MetricsOverviewDTO dto = service.overview(20);

        assertThat(dto.totalImpressions()).isZero();
        assertThat(dto.contexts()).hasSize(FeedbackContext.values().length);
        assertThat(dto.contexts()).allSatisfy(c -> {
            assertThat(c.impressions()).isZero();
            assertThat(c.upRate()).isZero();
        });
        assertThat(dto.topGames()).isEmpty();
        verify(gameRepository, never()).findByAppidIn(any());
    }

    @Test
    void overview_computesUpRatePerContext() {
        when(impressionRepository.count()).thenReturn(10L);
        when(impressionRepository.countByContext()).thenReturn(List.of(new ImpCtx(FeedbackContext.INPUT, 10L)));
        when(feedbackRepository.countByContextAndSignal()).thenReturn(List.of(
                new FbRow(FeedbackContext.INPUT, FeedbackSignal.UP, 2L),
                new FbRow(FeedbackContext.INPUT, FeedbackSignal.DOWN, 1L),
                new FbRow(FeedbackContext.INPUT, FeedbackSignal.CLICK, 4L)));
        when(impressionRepository.topAppids(any())).thenReturn(List.of());

        MetricsOverviewDTO dto = service.overview(20);

        MetricsOverviewDTO.ContextMetric input = dto.contexts().stream()
                .filter(c -> c.context() == FeedbackContext.INPUT).findFirst().orElseThrow();
        assertThat(input.impressions()).isEqualTo(10L);
        assertThat(input.up()).isEqualTo(2L);
        assertThat(input.down()).isEqualTo(1L);
        assertThat(input.click()).isEqualTo(4L);
        assertThat(input.upRate()).isEqualTo(0.2);
    }

    @Test
    void overview_topGames_joinsNamesAndPreservesOrder() {
        when(impressionRepository.count()).thenReturn(8L);
        when(impressionRepository.countByContext()).thenReturn(List.of());
        when(feedbackRepository.countByContextAndSignal()).thenReturn(List.of());
        when(impressionRepository.topAppids(any())).thenReturn(List.of(
                new ImpApp(10L, 5L), new ImpApp(20L, 3L)));
        Game g = new Game();
        g.setAppid(10L);
        g.setName("Alpha");
        when(gameRepository.findByAppidIn(List.of(10L, 20L))).thenReturn(List.of(g));

        MetricsOverviewDTO dto = service.overview(20);

        assertThat(dto.topGames()).hasSize(2);
        assertThat(dto.topGames().get(0).appid()).isEqualTo(10L);
        assertThat(dto.topGames().get(0).name()).isEqualTo("Alpha");
        assertThat(dto.topGames().get(0).impressions()).isEqualTo(5L);
        assertThat(dto.topGames().get(1).appid()).isEqualTo(20L);
        assertThat(dto.topGames().get(1).name()).isNull();
        assertThat(dto.topGames().get(1).impressions()).isEqualTo(3L);
    }
}
