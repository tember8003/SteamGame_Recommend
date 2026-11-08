package SteamGame.recommend.domain.metrics.service;

import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.metrics.DashboardRange;
import SteamGame.recommend.domain.metrics.dto.MetricsDashboardDTO;
import SteamGame.recommend.domain.metrics.repository.DashboardQueries;
import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsDashboardServiceTest {

    // 한국 시간 2026-11-08 12:00
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T03:00:00Z"), ZoneOffset.UTC);

    private final DashboardQueries queries = mock(DashboardQueries.class);
    private final GameRepository games = mock(GameRepository.class);
    private final TasteContentLoader taste = new TasteContentLoader(new ObjectMapper());
    private MetricsDashboardService service;

    // 첫 이벤트 날 조회는 표 세 개를 보므로 전체 기간에서만 부른다
    @Test
    void firstEventDateIsQueriedOnlyForAllRange() {
        service.dashboard(DashboardRange.D7);
        verify(queries, never()).firstEventDate();

        when(queries.firstEventDate()).thenReturn(Optional.of(LocalDate.of(2026, 11, 6)));
        MetricsDashboardDTO all = service.dashboard(DashboardRange.ALL);
        assertThat(all.from()).isEqualTo("2026-11-06");
        assertThat(all.trend()).hasSize(3);
    }

    @BeforeEach
    void setUp() {
        service = new MetricsDashboardService(queries, games, taste, CLOCK);
        when(queries.firstEventDate()).thenReturn(Optional.empty());
        when(queries.funnel(any())).thenReturn(new DashboardQueries.Funnel(10, 6, 5, 4, 2, 3, 2, 1));
        when(queries.trend(any())).thenReturn(List.of(new DashboardQueries.Daily(LocalDate.of(2026, 11, 4), 3, 2, 1)));
        when(queries.countByRef(anyString(), any())).thenReturn(Map.of());
        when(queries.countLibraryCode(anyString(), any())).thenReturn(Map.of());
        when(queries.libraryRecordedSince()).thenReturn(Optional.empty());
        when(queries.shares(any())).thenReturn(List.of());
        when(queries.impressionsByContext(any())).thenReturn(Map.of());
        when(queries.feedback(any())).thenReturn(List.of());
        when(queries.topImpressions(any(), anyInt())).thenReturn(List.of());
    }

    @Test
    void headerFieldsUseKoreanDates() {
        MetricsDashboardDTO d = service.dashboard(DashboardRange.D7);
        assertThat(d.range()).isEqualTo("7d");
        assertThat(d.from()).isEqualTo("2026-11-02");
        assertThat(d.to()).isEqualTo("2026-11-08");
        assertThat(d.timezone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void funnelStepsKeepSpecOrder() {
        MetricsDashboardDTO d = service.dashboard(DashboardRange.D7);
        assertThat(d.funnels().taste()).extracting(MetricsDashboardDTO.Step::step)
                .containsExactly("LANDING_VISIT", "FEATURE_SELECT", "TEST_START", "TEST_COMPLETE", "SHARE_CLICK");
        assertThat(d.funnels().taste()).extracting(MetricsDashboardDTO.Step::visitors).containsExactly(10L, 6L, 5L, 4L, 2L);
        assertThat(d.funnels().library()).extracting(MetricsDashboardDTO.Step::step)
                .containsExactly("LANDING_VISIT", "FEATURE_SELECT", "LIBRARY_COMPLETE", "SHARE_CLICK");
        assertThat(d.funnels().library()).extracting(MetricsDashboardDTO.Step::visitors).containsExactly(10L, 3L, 2L, 1L);
    }

    // 7일 중 이벤트가 있는 날은 하루뿐이어도 7일이 모두 나온다
    @Test
    void trendFillsEmptyDaysWithZero() {
        List<MetricsDashboardDTO.Daily> t = service.dashboard(DashboardRange.D7).trend();
        assertThat(t).hasSize(7);
        assertThat(t.get(0)).isEqualTo(new MetricsDashboardDTO.Daily("2026-11-02", 0, 0, 0));
        assertThat(t.get(2)).isEqualTo(new MetricsDashboardDTO.Daily("2026-11-04", 3, 2, 1));
        assertThat(t.get(6).date()).isEqualTo("2026-11-08");
    }

    @Test
    void distributionListsEveryCodeIncludingZero() {
        when(queries.countByRef(org.mockito.ArgumentMatchers.eq("TEST_COMPLETE"), any())).thenReturn(Map.of("EMCS", 3L));
        when(queries.countLibraryCode(org.mockito.ArgumentMatchers.eq("genre_code"), any())).thenReturn(Map.of("ACTION_SHOOTER", 2L));
        when(queries.countLibraryInsufficient(any())).thenReturn(1L);

        MetricsDashboardDTO.Distribution dist = service.dashboard(DashboardRange.D7).distribution();

        assertThat(dist.features()).extracting(MetricsDashboardDTO.CodeCount::code).containsExactly("TASTE", "SIMILAR", "LIBRARY");
        assertThat(dist.tasteTypes()).hasSize(16).contains(new MetricsDashboardDTO.CodeCount("EMCS", 3));
        assertThat(dist.libraryLedger()).hasSize(6);
        assertThat(dist.libraryCrime()).hasSize(5);
        // 장르 9종 뒤에 기록 부족(INSUFFICIENT)을 붙인다
        assertThat(dist.libraryGenre()).hasSize(10)
                .contains(new MetricsDashboardDTO.CodeCount("ACTION_SHOOTER", 2), new MetricsDashboardDTO.CodeCount("INSUFFICIENT", 1));
        assertThat(dist.libraryRecordedSince()).isNull();
    }

    @Test
    void sharesComputeInflowPerClickAndNullWhenNoClicks() {
        when(queries.shares(any())).thenReturn(List.of(
                new DashboardQueries.ShareCount("TASTE", "SHARE_CLICK", "LINK", 4),
                new DashboardQueries.ShareCount("TASTE", "SHARE_CLICK", "IMAGE", 1),
                new DashboardQueries.ShareCount("TASTE", "SHARE_INFLOW", null, 2),
                new DashboardQueries.ShareCount("LIBRARY", "SHARE_INFLOW", null, 3)));

        MetricsDashboardDTO.Shares s = service.dashboard(DashboardRange.D7).shares();

        assertThat(s.taste()).isEqualTo(new MetricsDashboardDTO.ShareStat(4, 1, 2, 0.4));
        assertThat(s.library()).isEqualTo(new MetricsDashboardDTO.ShareStat(0, 0, 3, null));
    }

    @Test
    void recommendationsListEveryContextAndUpRate() {
        when(queries.impressionsByContext(any())).thenReturn(Map.of("INPUT", 10L));
        when(queries.feedback(any())).thenReturn(List.of(new DashboardQueries.FeedbackCount("INPUT", "UP", 4)));

        MetricsDashboardDTO.Recommendations r = service.dashboard(DashboardRange.D7).recommendations();

        assertThat(r.totalImpressions()).isEqualTo(10);
        assertThat(r.contexts()).hasSize(SteamGame.recommend.domain.feedback.entity.FeedbackContext.values().length);
        assertThat(r.contexts().get(0)).isEqualTo(new MetricsDashboardDTO.ContextMetric("INPUT", 10, 4, 0, 0, 0.4));
    }
}
