package SteamGame.recommend.domain.metrics.service;

import SteamGame.recommend.domain.metrics.dto.MetricsStatusDTO;
import SteamGame.recommend.domain.metrics.repository.StatusQueries;
import SteamGame.recommend.domain.recommendation.similar.RecommendProperties;
import SteamGame.recommend.exception.RecentErrors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsStatusServiceTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-08T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final StatusQueries queries = mock(StatusQueries.class);
    private final RecentErrors recentErrors = mock(RecentErrors.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final RecommendProperties recommendProperties = new RecommendProperties();

    @BeforeEach
    void setUp() {
        when(context.getStartupDate()).thenReturn(STARTED_AT.toEpochMilli());
        when(queries.flywayVersion()).thenReturn(Optional.of("10"));
        when(queries.recentEvents(anyInt())).thenReturn(List.of());
        when(recentErrors.snapshot()).thenReturn(new RecentErrors.Snapshot(List.of(), List.of()));
        when(queries.jobSummaries(anyList(), any())).thenAnswer(inv -> {
            List<String> types = inv.getArgument(0);
            return types.stream().map(MetricsStatusServiceTest::none).toList();
        });
        when(queries.collectionData(any())).thenReturn(new StatusQueries.CollectionData(0, 0, 0, 0, null, null));
    }

    private MetricsStatusService service(String revision) {
        return new MetricsStatusService(queries, recentErrors, context, recommendProperties, revision, CLOCK);
    }

    private static StatusQueries.JobSummary none(String type) {
        return new StatusQueries.JobSummary(type, null, null, null, null, null, null, false, null, null, null, 0, 0);
    }

    @Test
    void status_combinesRevisionStartupAndFlywayVersion() {
        MetricsStatusService service = service("abc1234");

        MetricsStatusDTO dto = service.status();

        assertThat(dto.server().revision()).isEqualTo("abc1234");
        assertThat(dto.server().startedAt()).isEqualTo("2026-11-08T00:00:00Z");
        assertThat(dto.server().flywayVersion()).isEqualTo("10");
        assertThat(dto.errors().since()).isEqualTo("2026-11-08T00:00:00Z");
    }

    @Test
    void status_flywayVersionEmptyBecomesNull() {
        when(queries.flywayVersion()).thenReturn(Optional.empty());

        MetricsStatusDTO dto = service("abc").status();

        assertThat(dto.server().flywayVersion()).isNull();
    }

    @Test
    void status_revisionFlowsThroughUnchanged() {
        MetricsStatusDTO dto = service("unknown").status();

        assertThat(dto.server().revision()).isEqualTo("unknown");
    }

    @Test
    void status_recentEventsRequestsThirtyAndMapsColumns() {
        when(queries.recentEvents(30)).thenReturn(List.of(
                new StatusQueries.Event(Instant.parse("2026-11-08T01:00:00Z"), "TEST_COMPLETE", "EMCS", null, null, null, null)));

        MetricsStatusDTO dto = service("abc").status();

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

        MetricsStatusDTO dto = service("abc").status();

        assertThat(dto.recentEvents().get(0).at()).isEqualTo("2026-11-08T01:00:00.123Z");
        assertThat(dto.errors().recent().get(0).at()).isEqualTo("2026-11-08T01:30:00.987Z");
    }

    @Test
    void status_errorsCopiedFromRecorderSnapshot() {
        when(recentErrors.snapshot()).thenReturn(new RecentErrors.Snapshot(
                List.of(new RecentErrors.CodeCount(500, "INTERNAL_SERVER_ERROR", 3)),
                List.of(new RecentErrors.Entry(Instant.parse("2026-11-08T01:30:00Z"), 500, "INTERNAL_SERVER_ERROR", "/api/a"))));

        MetricsStatusDTO dto = service("abc").status();

        assertThat(dto.errors().counts()).containsExactly(new MetricsStatusDTO.CodeCount(500, "INTERNAL_SERVER_ERROR", 3));
        assertThat(dto.errors().recent()).containsExactly(
                new MetricsStatusDTO.ErrorEntry("2026-11-08T01:30:00Z", 500, "INTERNAL_SERVER_ERROR", "/api/a"));
    }

    // 작업은 정해진 다섯 종류를 고정 순서로 묻고, 7일과 24시간 기준 시각은 주입한 시계에서 계산한다
    @Test
    void collection_requestsKnownJobsInOrderWithCutoffsFromClock() {
        MetricsStatusDTO dto = service("abc").status();

        verify(queries).jobSummaries(
                List.of("steam_catalog", "tag_cooccurrence", "detail_rolling", "embed_backfill", "tag_embedding"),
                NOW.minus(Duration.ofDays(7)));
        verify(queries).collectionData(NOW.minus(Duration.ofHours(24)));
        assertThat(dto.collection().jobs()).extracting(MetricsStatusDTO.Job::batchType)
                .containsExactly("steam_catalog", "tag_cooccurrence", "detail_rolling", "embed_backfill", "tag_embedding");
    }

    // 기록이 없는 작업은 칸이 비고 횟수는 0이며, 주기가 있으면 주기 초과로 본다
    @Test
    void collection_jobWithoutRecordsIsEmptyAndStaleWhenItHasAnInterval() {
        MetricsStatusDTO dto = service("abc").status();

        MetricsStatusDTO.Job catalog = dto.collection().jobs().get(0);
        assertThat(catalog).isEqualTo(new MetricsStatusDTO.Job("steam_catalog", null, null, null, null, null,
                false, false, false, null, null, 26, true, 0, 0));
        MetricsStatusDTO.Job gnn = dto.collection().jobs().get(4);
        assertThat(gnn.expectedIntervalHours()).isNull();
        assertThat(gnn.stale()).isFalse();
    }

    // 마지막 성공이 12시간 전이라 7시간 주기를 넘겨 주기 초과다
    @Test
    void collection_mapsJobSummaryWithMillisTimes() {
        when(queries.jobSummaries(anyList(), any())).thenReturn(List.of(
                none("steam_catalog"), none("tag_cooccurrence"),
                new StatusQueries.JobSummary("detail_rolling",
                        Instant.parse("2026-11-20T06:00:00.123456Z"), null, 0, 0, null,
                        Instant.parse("2026-11-20T00:05:00.987654Z"), true,
                        Instant.parse("2026-11-20T00:05:00.987654Z"), 120, null, 4, 1),
                none("embed_backfill"), none("tag_embedding")));

        MetricsStatusDTO.Job detail = service("abc").status().collection().jobs().get(2);

        assertThat(detail).isEqualTo(new MetricsStatusDTO.Job("detail_rolling",
                "2026-11-20T06:00:00.123Z", null, 0, 0, null,
                true, true, false, "2026-11-20T00:05:00.987Z", 120, 7, true, 4, 1));
    }

    @Test
    void collection_mapsDataFigures() {
        when(queries.collectionData(any())).thenReturn(new StatusQueries.CollectionData(44656, 12, 300, 0,
                Instant.parse("2026-11-20T11:00:00.555555Z"), null));

        MetricsStatusDTO.Data data = service("abc").status().collection().data();

        assertThat(data).isEqualTo(new MetricsStatusDTO.Data(44656, 12, 300, 0, "2026-11-20T11:00:00.555Z", null));
    }

    @Test
    void collection_flagReflectsRecommendProperties() {
        assertThat(service("abc").status().collection().flags())
                .containsExactly(new MetricsStatusDTO.Flag("similarInterleave", false));

        recommendProperties.setSimilarInterleaveEnabled(true);

        assertThat(service("abc").status().collection().flags())
                .containsExactly(new MetricsStatusDTO.Flag("similarInterleave", true));
    }

    // 경고는 판정 규칙 결과를 그대로 싣는다, 상세 수집이 24시간 안에 끝났는데 본문 저장이 0이면 경고가 붙음
    @Test
    void collection_carriesWarningsFromHealthRules() {
        Instant recent = NOW.minus(Duration.ofHours(1));
        when(queries.jobSummaries(anyList(), any())).thenReturn(List.of(
                new StatusQueries.JobSummary("steam_catalog", recent, recent, 39958, 0, "pages=141",
                        recent, false, recent, 39958, 39900, 1, 0),
                new StatusQueries.JobSummary("tag_cooccurrence", recent, recent, 1, 0, "pairs=74389",
                        recent, false, recent, 1, null, 1, 0),
                new StatusQueries.JobSummary("detail_rolling", recent, recent, 0, 0, "withText=0 noText=0",
                        recent, false, recent, 0, null, 1, 0),
                new StatusQueries.JobSummary("embed_backfill", recent, recent, 1000, 0, "processed=1000 failed=0 (api_down)",
                        recent, false, recent, 1000, null, 1, 0),
                none("tag_embedding")));

        assertThat(service("abc").status().collection().warnings())
                .containsExactly(new MetricsStatusDTO.Warning("DETAIL_NO_WRITES", "detail_rolling"));
    }

    // 작업이 note 에 남긴 예외 메시지 원문은 응답에 실리지 않는다, 실패 여부는 원문 기준으로 그대로 판단함
    @Test
    void collection_rawFailureNoteNeverReachesResponse() {
        String raw = "FAILED: ERROR: duplicate key value violates unique constraint \"games_pkey\" Detail: Key (appid)=(570)";
        Instant at = NOW.minus(Duration.ofHours(1));
        when(queries.jobSummaries(anyList(), any())).thenReturn(List.of(
                new StatusQueries.JobSummary("steam_catalog", at, at, 10, 1, raw, at, true, null, null, null, 1, 1),
                none("tag_cooccurrence"), none("detail_rolling"), none("embed_backfill"), none("tag_embedding")));

        MetricsStatusDTO.Job catalog = service("abc").status().collection().jobs().get(0);

        assertThat(catalog.lastNote()).isEqualTo("FAILED");
        assertThat(catalog.lastRunFailed()).isTrue();
    }

    // 24시간 넘게 끝나지 않은 실행은 실행 중이 아니라 멈춤으로 내보낸다, 딱 24시간은 아직 실행 중임
    @Test
    void collection_unfinishedRunOlderThan24HoursIsStuckNotRunning() {
        when(queries.jobSummaries(anyList(), any())).thenReturn(List.of(
                none("steam_catalog"), none("tag_cooccurrence"),
                new StatusQueries.JobSummary("detail_rolling", NOW.minus(Duration.ofHours(24)), null, 0, 0, null,
                        null, false, NOW.minus(Duration.ofHours(1)), 5, null, 1, 0),
                none("embed_backfill"),
                new StatusQueries.JobSummary("tag_embedding", NOW.minus(Duration.ofHours(24)).minusSeconds(1), null,
                        0, 0, null, null, false, null, null, null, 1, 0)));

        MetricsStatusDTO.Collection c = service("abc").status().collection();

        MetricsStatusDTO.Job detail = c.jobs().get(2);
        assertThat(detail.running()).isTrue();
        assertThat(detail.stuck()).isFalse();
        MetricsStatusDTO.Job gnn = c.jobs().get(4);
        assertThat(gnn.running()).isFalse();
        assertThat(gnn.stuck()).isTrue();
        assertThat(c.warnings()).contains(new MetricsStatusDTO.Warning("JOB_STUCK", "tag_embedding"));
        assertThat(c.warnings()).doesNotContain(new MetricsStatusDTO.Warning("JOB_STUCK", "detail_rolling"));
    }
}
