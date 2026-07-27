package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.AppTag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
public class DetailCrawlerService {

    private final SteamSpyClient spyClient;
    private final SteamStoreClient storeClient;
    private final SteamSpyAppDetailsParser tagsParser;
    private final StorefrontParser storefrontParser;
    private final AppReviewsParser reviewsParser;
    private final DetailUpsertRepository upsert;
    private final JdbcTemplate jdbc;
    private final CrawlerProperties props;

    public DetailCrawlerService(SteamSpyClient spyClient, SteamStoreClient storeClient,
                                SteamSpyAppDetailsParser tagsParser, StorefrontParser storefrontParser,
                                AppReviewsParser reviewsParser, DetailUpsertRepository upsert,
                                JdbcTemplate jdbc, CrawlerProperties props) {
        this.spyClient = spyClient;
        this.storeClient = storeClient;
        this.tagsParser = tagsParser;
        this.storefrontParser = storefrontParser;
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
                List<AppTag> tags = tagsParser.parseTags(spyClient.fetchAppDetails(appid));
                // upsertTags 는 DELETE 후 재삽입 방식이라, 빈 파싱 결과(진짜 태그 없는 게임이거나
                // SteamSpy 의 일시적 빈 응답)로 교체하면 다음 성공 크롤링 전까지 기존 가중 태그가 날아감
                // 파싱 결과가 비면 교체를 건너뛰어 기존 태그를 보존
                if (!tags.isEmpty()) {
                    upsert.upsertTags(appid, tags);
                }
                sleep(props.getDetailDelayMs());

                storefrontParser.parse(appid, storeClient.fetchAppDetails(appid))
                        .ifPresent(upsert::upsertStorefront);
                sleep(props.getDetailDelayMs());

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
