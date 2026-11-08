package SteamGame.recommend.exception;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RecentErrorsTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void record_누적한_횟수를_내림차순으로_돌려준다() {
        RecentErrors errors = new RecentErrors(FIXED);

        errors.record(500, "INTERNAL_SERVER_ERROR", "/api/a");
        errors.record(500, "INTERNAL_SERVER_ERROR", "/api/a");
        errors.record(400, "VALIDATION_ERROR", "/api/b");

        List<RecentErrors.CodeCount> counts = errors.snapshot().counts();

        assertThat(counts).hasSize(2);
        assertThat(counts.get(0).status()).isEqualTo(500);
        assertThat(counts.get(0).errorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(counts.get(0).count()).isEqualTo(2);
        assertThat(counts.get(1).status()).isEqualTo(400);
        assertThat(counts.get(1).count()).isEqualTo(1);
    }

    @Test
    void record_같은_코드라도_상태가_다르면_따로_센다() {
        RecentErrors errors = new RecentErrors(FIXED);

        errors.record(502, "STEAM_API_ERROR", "/api/a");
        errors.record(500, "STEAM_API_ERROR", "/api/a");

        List<RecentErrors.CodeCount> counts = errors.snapshot().counts();

        assertThat(counts).hasSize(2);
        assertThat(counts).allSatisfy(c -> assertThat(c.count()).isEqualTo(1));
    }

    @Test
    void record_최근_목록에_시각과_경로를_그대로_담는다() {
        RecentErrors errors = new RecentErrors(FIXED);

        errors.record(500, "INTERNAL_SERVER_ERROR", "/api/library/{steamId}");

        RecentErrors.Entry entry = errors.snapshot().recent().get(0);

        assertThat(entry.at()).isEqualTo(FIXED.instant());
        assertThat(entry.status()).isEqualTo(500);
        assertThat(entry.errorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(entry.route()).isEqualTo("/api/library/{steamId}");
    }

    @Test
    void record_최근_목록은_30건을_넘으면_오래된_것부터_버리고_최신순으로_남긴다() {
        RecentErrors errors = new RecentErrors(FIXED);

        for (int i = 0; i < 35; i++) {
            errors.record(500, "INTERNAL_SERVER_ERROR", "/api/" + i);
        }

        List<RecentErrors.Entry> recent = errors.snapshot().recent();

        assertThat(recent).hasSize(30);
        assertThat(recent.get(0).route()).isEqualTo("/api/34");
        assertThat(recent.get(29).route()).isEqualTo("/api/5");
    }

    @Test
    void count_횟수만_올리고_최근_목록에는_넣지_않는다() {
        RecentErrors errors = new RecentErrors(FIXED);

        errors.count(404, "NOT_FOUND");
        errors.count(404, "NOT_FOUND");

        RecentErrors.Snapshot snapshot = errors.snapshot();

        assertThat(snapshot.counts()).hasSize(1);
        assertThat(snapshot.counts().get(0).count()).isEqualTo(2);
        assertThat(snapshot.recent()).isEmpty();
    }

    @Test
    void record_여러_스레드가_동시에_불러도_합계가_정확하다() throws InterruptedException {
        RecentErrors errors = new RecentErrors(FIXED);
        int threads = 8;
        int perThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        errors.record(500, "INTERNAL_SERVER_ERROR", "/api/concurrent");
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        long total = errors.snapshot().counts().stream()
                .mapToLong(RecentErrors.CodeCount::count)
                .sum();

        assertThat(total).isEqualTo((long) threads * perThread);
        assertThat(errors.snapshot().recent()).hasSize(30);
    }
}
