package SteamGame.recommend.infra.crawler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Slf4j
@Service
public class TagCooccurrenceRefresher {

    private final TagCooccurrenceWriter writer;
    private final JdbcTemplate jdbc;

    public TagCooccurrenceRefresher(TagCooccurrenceWriter writer, JdbcTemplate jdbc) {
        this.writer = writer;
        this.jdbc = jdbc;
    }

    // 기록은 계산 트랜잭션 밖에서 남겨, 계산이 되돌려져도 실패 기록은 남게 함
    public int refresh() {
        Long logId = startCrawlLog();
        try {
            int pairs = writer.replaceAll();
            finishCrawlLog(logId, pairs, 0, "pairs=" + pairs);
            log.info("tag cooccurrence refreshed: pairs={}", pairs);
            return pairs;
        } catch (RuntimeException ex) {
            log.error("tag cooccurrence refresh failed", ex);
            finishCrawlLog(logId, 0, 1, "FAILED: " + ex.getMessage());
            throw ex;
        }
    }

    private Long startCrawlLog() {
        try {
            return jdbc.queryForObject("""
                INSERT INTO crawl_log (started_at, batch_type) VALUES (?, 'tag_cooccurrence')
                RETURNING id""", Long.class, OffsetDateTime.now());
        } catch (Exception e) {
            log.error("Could not create crawl_log row", e);
            throw new IllegalStateException("crawl_log 기록을 시작하지 못함", e);
        }
    }

    private void finishCrawlLog(Long id, int processed, int failed, String note) {
        if (id == null) return;
        try {
            jdbc.update("""
                UPDATE crawl_log SET finished_at = ?, processed_count = ?, failed_count = ?, note = ?
                WHERE id = ?""", OffsetDateTime.now(), processed, failed, note, id);
        } catch (Exception e) {
            log.warn("Could not finalize crawl_log row {}", id, e);
        }
    }
}
