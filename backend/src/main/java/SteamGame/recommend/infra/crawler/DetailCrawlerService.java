package SteamGame.recommend.infra.crawler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
public class DetailCrawlerService {

    private final SteamStoreClient storeClient;
    private final AppReviewsParser reviewsParser;
    private final DetailUpsertRepository upsert;
    private final JdbcTemplate jdbc;
    private final CrawlerProperties props;

    public DetailCrawlerService(SteamStoreClient storeClient, AppReviewsParser reviewsParser,
                                DetailUpsertRepository upsert, JdbcTemplate jdbc, CrawlerProperties props) {
        this.storeClient = storeClient;
        this.reviewsParser = reviewsParser;
        this.upsert = upsert;
        this.jdbc = jdbc;
        this.props = props;
    }

    public record DetailResult(int processed, int failed) {}

    public DetailResult runDetailBatch() {
        Long logId = startCrawlLog();
        List<Long> appids = upsert.selectNextToCrawl(props.getDailyDetailBatchSize());
        int processed = 0, failed = 0;

        for (Long appid : appids) {
            try {
                // 태그와 게임 정보는 catalog 배치가 맡고 여기서는 리뷰 본문만 받음
                reviewsParser.parse(appid, storeClient.fetchAppReviews(appid), props.getReviewSampleMaxChars())
                        .ifPresent(upsert::upsertReviewDetail);
                sleep(props.getDetailDelayMs());
                processed++;
            } catch (Exception e) {
                failed++;
                log.warn("detail crawl failed for appid {} — skipping", appid, e);
            } finally {
                // 성공/실패 모두 큐 뒤로 회전시켜 poison appid 가 큐 헤드를 막지 않도록 함
                try { upsert.markCrawled(appid, OffsetDateTime.now()); }
                catch (Exception e) { log.warn("markCrawled failed for appid {}: {}", appid, e.getMessage()); }
            }
        }

        finishCrawlLog(logId, processed, failed, "processed=" + processed + " failed=" + failed);
        log.info("detail rolling batch done: processed={}, failed={}", processed, failed);
        return new DetailResult(processed, failed);
    }

    private void sleep(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private Long startCrawlLog() {
        try {
            return jdbc.queryForObject("""
                INSERT INTO crawl_log (started_at, batch_type) VALUES (?, 'detail_rolling')
                RETURNING id""", Long.class, OffsetDateTime.now());
        } catch (Exception e) {
            log.warn("Could not create crawl_log row: {}", e.getMessage());
            return null;
        }
    }

    private void finishCrawlLog(Long id, int processed, int failed, String note) {
        if (id == null) return;
        try {
            jdbc.update("""
                UPDATE crawl_log SET finished_at = ?, processed_count = ?, failed_count = ?, note = ?
                WHERE id = ?""", OffsetDateTime.now(), processed, failed, note, id);
        } catch (Exception e) {
            log.warn("Could not finalize crawl_log row {}: {}", id, e.getMessage());
        }
    }
}
