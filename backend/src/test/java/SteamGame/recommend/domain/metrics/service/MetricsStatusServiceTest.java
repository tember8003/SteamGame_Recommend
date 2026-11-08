package SteamGame.recommend.domain.metrics.service;

import SteamGame.recommend.domain.metrics.dto.MetricsStatusDTO;
import SteamGame.recommend.domain.metrics.repository.StatusQueries;
import SteamGame.recommend.exception.RecentErrors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsStatusServiceTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-08T00:00:00Z");

    private final StatusQueries queries = mock(StatusQueries.class);
    private final RecentErrors recentErrors = mock(RecentErrors.class);
    private final ApplicationContext context = mock(ApplicationContext.class);

    @BeforeEach
    void setUp() {
        when(context.getStartupDate()).thenReturn(STARTED_AT.toEpochMilli());
        when(queries.flywayVersion()).thenReturn(Optional.of("10"));
        when(queries.recentEvents(anyInt())).thenReturn(List.of());
        when(recentErrors.snapshot()).thenReturn(new RecentErrors.Snapshot(List.of(), List.of()));
    }

    @Test
    void status_combinesRevisionStartupAndFlywayVersion() {
        MetricsStatusService service = new MetricsStatusService(queries, recentErrors, context, "abc1234");

        MetricsStatusDTO dto = service.status();

        assertThat(dto.server().revision()).isEqualTo("abc1234");
        assertThat(dto.server().startedAt()).isEqualTo("2026-11-08T00:00:00Z");
        assertThat(dto.server().flywayVersion()).isEqualTo("10");
        assertThat(dto.errors().since()).isEqualTo("2026-11-08T00:00:00Z");
    }

    @Test
    void status_flywayVersionEmptyBecomesNull() {
        when(queries.flywayVersion()).thenReturn(Optional.empty());

        MetricsStatusDTO dto = new MetricsStatusService(queries, recentErrors, context, "abc").status();

        assertThat(dto.server().flywayVersion()).isNull();
    }

    @Test
    void status_revisionFlowsThroughUnchanged() {
        MetricsStatusDTO dto = new MetricsStatusService(queries, recentErrors, context, "unknown").status();

        assertThat(dto.server().revision()).isEqualTo("unknown");
    }

    @Test
    void status_recentEventsRequestsThirtyAndMapsColumns() {
        when(queries.recentEvents(30)).thenReturn(List.of(
                new StatusQueries.Event(Instant.parse("2026-11-08T01:00:00Z"), "TEST_COMPLETE", "EMCS", null, null, null, null)));

        MetricsStatusDTO dto = new MetricsStatusService(queries, recentErrors, context, "abc").status();

        verify(queries).recentEvents(30);
        assertThat(dto.recentEvents()).containsExactly(
                new MetricsStatusDTO.Event("2026-11-08T01:00:00Z", "TEST_COMPLETE", "EMCS", null, null, null, null));
    }

    // DB와 시계는 마이크로초까지 담지만 응답 시각은 밀리초까지만 내보낸다
    @Test
    void status_timesAreTruncatedToMillis() {
        when(queries.recentEvents(30)).thenReturn(List.of(
                new StatusQueries.Event(Instant.parse("2026-11-08T01:00:00.123456Z"), "LANDING_VISIT", null, null, null, null, null)));
        when(recentErrors.snapshot()).thenReturn(new RecentErrors.Snapshot(List.of(),
                List.of(new RecentErrors.Entry(Instant.parse("2026-11-08T01:30:00.987654Z"), 400, "BAD_REQUEST", "/api/a"))));

        MetricsStatusDTO dto = new MetricsStatusService(queries, recentErrors, context, "abc").status();

        assertThat(dto.recentEvents().get(0).at()).isEqualTo("2026-11-08T01:00:00.123Z");
        assertThat(dto.errors().recent().get(0).at()).isEqualTo("2026-11-08T01:30:00.987Z");
    }

    @Test
    void status_errorsCopiedFromRecorderSnapshot() {
        when(recentErrors.snapshot()).thenReturn(new RecentErrors.Snapshot(
                List.of(new RecentErrors.CodeCount(500, "INTERNAL_SERVER_ERROR", 3)),
                List.of(new RecentErrors.Entry(Instant.parse("2026-11-08T01:30:00Z"), 500, "INTERNAL_SERVER_ERROR", "/api/a"))));

        MetricsStatusDTO dto = new MetricsStatusService(queries, recentErrors, context, "abc").status();

        assertThat(dto.errors().counts()).containsExactly(new MetricsStatusDTO.CodeCount(500, "INTERNAL_SERVER_ERROR", 3));
        assertThat(dto.errors().recent()).containsExactly(
                new MetricsStatusDTO.ErrorEntry("2026-11-08T01:30:00Z", 500, "INTERNAL_SERVER_ERROR", "/api/a"));
    }
}
