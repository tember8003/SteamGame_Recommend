package SteamGame.recommend.domain.metrics;

import SteamGame.recommend.domain.metrics.dto.MetricsStatusDTO;
import SteamGame.recommend.domain.metrics.repository.StatusQueries;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionHealthTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    // 마지막 실행이 성공으로 끝난 요약, 필요한 칸만 바꿔 쓴다
    private static StatusQueries.JobSummary ok(String type, Instant successAt, Integer processed, Integer previous) {
        return new StatusQueries.JobSummary(type, successAt.minus(Duration.ofMinutes(10)), successAt, processed, 0, "ok",
                successAt, false, successAt, processed, previous, 1, 0);
    }

    private static StatusQueries.JobSummary none(String type) {
        return new StatusQueries.JobSummary(type, null, null, null, null, null, null, false, null, null, null, 0, 0);
    }

    @Test
    void jobs_areFixedOrderWithExpectedIntervals() {
        assertThat(CollectionHealth.JOBS).containsExactly(
                new CollectionHealth.JobSpec("steam_catalog", 26),
                new CollectionHealth.JobSpec("tag_cooccurrence", 26),
                new CollectionHealth.JobSpec("detail_rolling", 7),
                new CollectionHealth.JobSpec("embed_backfill", 26),
                new CollectionHealth.JobSpec("tag_embedding", null));
    }

    @Test
    void stale_onlyWhenIntervalExceededOrNeverSucceeded() {
        assertThat(CollectionHealth.stale(7, NOW.minus(Duration.ofHours(7)), NOW)).isFalse();
        assertThat(CollectionHealth.stale(7, NOW.minus(Duration.ofHours(7)).minusSeconds(1), NOW)).isTrue();
        assertThat(CollectionHealth.stale(7, null, NOW)).isTrue();
        // 주기가 없는 수동 작업은 오래돼도, 한 번도 안 돌았어도 주기 초과가 아니다
        assertThat(CollectionHealth.stale(null, null, NOW)).isFalse();
        assertThat(CollectionHealth.stale(null, NOW.minus(Duration.ofDays(60)), NOW)).isFalse();
    }

    @Test
    void catalogDropped_whenLatestIsMoreThanFivePercentBelowPrevious() {
        assertThat(CollectionHealth.catalogDropped(95, 100)).isFalse();
        assertThat(CollectionHealth.catalogDropped(94, 100)).isTrue();
        assertThat(CollectionHealth.catalogDropped(120, 100)).isFalse();
        assertThat(CollectionHealth.catalogDropped(10, null)).isFalse();
        assertThat(CollectionHealth.catalogDropped(null, null)).isFalse();
    }

    // 상세 수집이 24시간 안에 끝났는데 리뷰 본문이 하나도 안 들어왔으면 경고한다
    @Test
    void detailNoWrites_onlyWhenARunFinishedWithin24hAndNothingLanded() {
        assertThat(CollectionHealth.detailNoWrites(NOW.minus(Duration.ofHours(1)), 0, NOW)).isTrue();
        assertThat(CollectionHealth.detailNoWrites(NOW.minus(Duration.ofHours(24)), 0, NOW)).isTrue();
        assertThat(CollectionHealth.detailNoWrites(NOW.minus(Duration.ofHours(1)), 3, NOW)).isFalse();
        assertThat(CollectionHealth.detailNoWrites(NOW.minus(Duration.ofHours(25)), 0, NOW)).isFalse();
        assertThat(CollectionHealth.detailNoWrites(null, 0, NOW)).isFalse();
    }

    @Test
    void warnings_emptyWhenEverythingIsHealthy() {
        Instant recent = NOW.minus(Duration.ofHours(1));
        List<StatusQueries.JobSummary> jobs = List.of(
                ok("steam_catalog", recent, 39958, 39900),
                ok("tag_cooccurrence", recent, 74389, null),
                ok("detail_rolling", recent, 100, null),
                ok("embed_backfill", recent, 1000, null),
                none("tag_embedding"));

        assertThat(CollectionHealth.warnings(jobs, 50, NOW)).isEmpty();
    }

    // 끝난 실행이 실패면 JOB_FAILED, 주기를 넘기면 JOB_STALE 을 작업 순서대로 내고 나머지 경고를 뒤에 붙인다
    @Test
    void warnings_listsFailedStaleDropAndNoWritesInOrder() {
        Instant recent = NOW.minus(Duration.ofHours(1));
        StatusQueries.JobSummary failedDetail = new StatusQueries.JobSummary("detail_rolling",
                recent.minus(Duration.ofMinutes(5)), recent, 0, 0, "FAILED: gone", recent, true,
                NOW.minus(Duration.ofDays(3)), 10, null, 4, 1);
        List<StatusQueries.JobSummary> jobs = List.of(
                ok("steam_catalog", recent, 30000, 39958),
                none("tag_cooccurrence"),
                failedDetail,
                ok("embed_backfill", recent, 1000, null),
                none("tag_embedding"));

        assertThat(CollectionHealth.warnings(jobs, 0, NOW)).containsExactly(
                new MetricsStatusDTO.Warning("JOB_STALE", "tag_cooccurrence"),
                new MetricsStatusDTO.Warning("JOB_FAILED", "detail_rolling"),
                new MetricsStatusDTO.Warning("JOB_STALE", "detail_rolling"),
                new MetricsStatusDTO.Warning("CATALOG_DROP", "steam_catalog"),
                new MetricsStatusDTO.Warning("DETAIL_NO_WRITES", "detail_rolling"));
    }

    // embed_backfill 은 무료 할당량 때문에 매일 (api_down) 을 남긴다, 실패 표식이 아니라 경고하지 않음
    @Test
    void warnings_ignoresApiDownNoteOnEmbedBackfill() {
        Instant recent = NOW.minus(Duration.ofHours(1));
        StatusQueries.JobSummary embed = new StatusQueries.JobSummary("embed_backfill", recent, recent, 1000, 0,
                "processed=1000 failed=0 (api_down)", recent, false, recent, 1000, null, 1, 0);

        assertThat(CollectionHealth.warnings(List.of(embed), 0, NOW)).isEmpty();
    }

    // 실행 중인 작업도 마지막 성공이 주기를 넘겼으면 주기 초과다
    @Test
    void warnings_runningJobPastIntervalIsStillStale() {
        StatusQueries.JobSummary running = new StatusQueries.JobSummary("embed_backfill",
                NOW.minus(Duration.ofMinutes(1)), null, 0, 0, null, NOW.minus(Duration.ofDays(2)), false,
                NOW.minus(Duration.ofDays(2)), 1000, null, 1, 0);

        assertThat(CollectionHealth.warnings(List.of(running), 0, NOW))
                .containsExactly(new MetricsStatusDTO.Warning("JOB_STALE", "embed_backfill"));
    }

    // 요약에 없는 작업 종류는 주기를 모르므로 주기 초과로 보지 않는다
    @Test
    void intervalOf_unknownTypeIsNull() {
        assertThat(CollectionHealth.intervalOf("steamspy_seed")).isNull();
        assertThat(CollectionHealth.intervalOf("detail_rolling")).isEqualTo(7);
    }

    // 예외 메시지 원문이 화면으로 새지 않게 아는 키의 값과 (api_down) 만 남긴다
    @Test
    void safeNote_dropsRawErrorTextFromCatalogFailure() {
        assertThat(CollectionHealth.safeNote(
                "FAILED: ERROR: duplicate key value violates unique constraint \"games_pkey\" Detail: Key (appid)=(570)"))
                .isEqualTo("FAILED");
    }

    @Test
    void safeNote_keepsCountsAfterKoreanFailureReason() {
        assertThat(CollectionHealth.safeNote(
                "FAILED: 응답 이상과 요청 실패가 절반을 넘음 withText=0 noText=0 malformed=9 failed=1"))
                .isEqualTo("FAILED withText=0 noText=0 malformed=9 failed=1");
    }

    @Test
    void safeNote_keepsKnownNotesIntact() {
        assertThat(CollectionHealth.safeNote("processed=1000 failed=0 (api_down)"))
                .isEqualTo("processed=1000 failed=0 (api_down)");
        assertThat(CollectionHealth.safeNote("pages=141 stop=end-of-results skipped=1 unknownTags=0"))
                .isEqualTo("pages=141 stop=end-of-results skipped=1 unknownTags=0");
        assertThat(CollectionHealth.safeNote("pairs=74389")).isEqualTo("pairs=74389");
        String gnn = "games=44656 tags=471 hubTags=Action,Indie,Singleplayer auc=0.9209 seconds=13435 cleared=0 gate=pass";
        assertThat(CollectionHealth.safeNote(gnn)).isEqualTo(gnn);
    }

    // 남는 것이 없으면 빈 문자열 대신 null 을 돌려줘 화면이 '-' 를 보이게 한다
    @Test
    void safeNote_nothingSafeLeftBecomesNull() {
        assertThat(CollectionHealth.safeNote("error=RuntimeError: boom")).isNull();
        assertThat(CollectionHealth.safeNote(null)).isNull();
    }

    // 아는 키라도 값에 허용하지 않는 글자가 섞이면 버린다
    @Test
    void safeNote_dropsKnownKeyWithUnsafeValue() {
        assertThat(CollectionHealth.safeNote("processed=1000 failed=<script>")).isEqualTo("processed=1000");
    }

    // 끝나지 않은 마지막 실행이 24시간을 넘기면 멈춤으로 본다, 딱 24시간은 아직 아님
    @Test
    void stuck_onlyWhenUnfinishedForMoreThan24Hours() {
        Instant started = NOW.minus(Duration.ofHours(24));
        assertThat(CollectionHealth.stuck(started, null, NOW)).isFalse();
        assertThat(CollectionHealth.stuck(started.minusSeconds(1), null, NOW)).isTrue();
        assertThat(CollectionHealth.stuck(started.minusSeconds(1), NOW, NOW)).isFalse();
        assertThat(CollectionHealth.stuck(null, null, NOW)).isFalse();
    }

    // 주기가 없는 GNN 학습도 프로세스가 죽어 끝나지 않으면 멈춤 경고가 붙는다
    @Test
    void warnings_stuckJobGetsJobStuckFirst() {
        StatusQueries.JobSummary dead = new StatusQueries.JobSummary("tag_embedding",
                NOW.minus(Duration.ofHours(30)), null, 0, 0, null, null, false, null, null, null, 1, 0);

        assertThat(CollectionHealth.warnings(List.of(dead), 1, NOW))
                .containsExactly(new MetricsStatusDTO.Warning("JOB_STUCK", "tag_embedding"));
    }
}
