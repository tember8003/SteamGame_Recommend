package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.StoreItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CatalogSyncService {

    public enum StopReason {
        END_OF_RESULTS("end-of-results");

        private final String label;

        StopReason(String label) { this.label = label; }

        public String label() { return label; }
    }

    public record SyncResult(int pages, int saved, int failed, StopReason stopReason) {}

    private final SteamWebApiClient client;
    private final StoreItemParser parser;
    private final CatalogUpsertRepository upsert;
    private final JdbcTemplate jdbc;
    private final CrawlerProperties props;

    public CatalogSyncService(SteamWebApiClient client, StoreItemParser parser,
                              CatalogUpsertRepository upsert, JdbcTemplate jdbc, CrawlerProperties props) {
        this.client = client;
        this.parser = parser;
        this.upsert = upsert;
        this.jdbc = jdbc;
        this.props = props;
    }

    public SyncResult runSync() {
        Long logId = startCrawlLog();
        int pages = 0, saved = 0, failed = 0, skipped = 0, unknownTags = 0;
        StopReason reason = null;
        try {
            Map<Integer, String> tagNames = parser.parseTagList(client.fetchTagList());
            // 이름표 없이 저장하면 게임마다 태그가 통째로 비어 버리므로 시작 전에 멈춤
            if (tagNames.isEmpty()) throw new IllegalStateException("태그 이름표가 비어 있어 동기화를 멈춤");

            int size = props.getCatalogPageSize();
            for (int page = 0; page < props.getCatalogMaxPages(); page++) {
                sleep(props.getCatalogCallDelayMs());
                StoreItemParser.QueryPage p = parser.parsePage(client.fetchQueryPage(page * size, size), tagNames);
                pages++;

                if (p.rawCount() == 0) {
                    // 전체 개수를 다 지난 빈 페이지만 진짜 끝이고, 그 밖의 빈 응답은 요청 제한이나 응답 누락일 수 있어 실패로 처리함
                    if (p.totalMatching() > 0 && (long) page * size >= p.totalMatching()) {
                        reason = StopReason.END_OF_RESULTS;
                        break;
                    }
                    throw new IllegalStateException("page " + page + " 에서 결과가 비어 있음, 전체 "
                            + p.totalMatching() + " 개 중 start " + (page * size));
                }
                // 게임이 있는데 하나도 못 읽었으면 응답 형식이 바뀐 것으로 보고 실패로 처리함
                if (p.items().isEmpty()) {
                    throw new IllegalStateException("page " + page + " 에서 읽을 수 있는 게임이 없음");
                }
                skipped += p.rawCount() - p.items().size();
                unknownTags += p.unknownTags();

                // 정렬이 리뷰 순이 아니라 기준을 넘는 게임이 없는 페이지도 흔하므로 멈추지 않고 넘어감
                List<StoreItem> keep = p.items().stream()
                        .filter(i -> i.reviewCount() >= props.getMinReviews())
                        .toList();
                if (keep.isEmpty()) continue;

                int pageSaved = 0;
                Exception lastError = null;
                for (StoreItem item : keep) {
                    try {
                        upsert.upsertItem(item);
                        saved++;
                        pageSaved++;
                    } catch (Exception e) {
                        failed++;
                        lastError = e;
                        log.warn("catalog upsert failed for appid {}, skipping", item.appid(), e);
                    }
                }
                // 한 페이지가 통째로 저장 실패면 DB나 스키마 문제라 계속 돌지 않음
                if (pageSaved == 0) {
                    throw new IllegalStateException("page " + page + " 의 저장이 모두 실패함", lastError);
                }
                log.info("steam catalog: page {} saved {} of {} ({} below min)",
                        page, pageSaved, p.rawCount(), p.items().size() - keep.size());
            }
            // 상한에 닿아 끝까지 못 돌았으면 일부만 갱신된 것이라 성공으로 남기지 않음
            if (reason == null) {
                throw new IllegalStateException("catalogMaxPages=" + props.getCatalogMaxPages()
                        + " 에 닿아 결과 끝까지 돌지 못함");
            }
            // 전체를 돌고도 저장이 0이면 리뷰 수 필드가 깨진 것으로 봄
            if (saved == 0) {
                throw new IllegalStateException(pages + " 페이지를 돌았는데 리뷰 기준을 넘는 게임이 하나도 없음");
            }
        } catch (RuntimeException ex) {
            log.error("steam catalog sync failed", ex);
            finishCrawlLog(logId, saved, failed + 1, "FAILED: " + ex.getMessage());
            throw ex;
        }

        finishCrawlLog(logId, saved, failed, "pages=" + pages + " stop=" + reason.label()
                + " skipped=" + skipped + " unknownTags=" + unknownTags);
        return new SyncResult(pages, saved, failed, reason);
    }

    private void sleep(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private Long startCrawlLog() {
        try {
            return jdbc.queryForObject("""
                INSERT INTO crawl_log (started_at, batch_type) VALUES (?, 'steam_catalog')
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
