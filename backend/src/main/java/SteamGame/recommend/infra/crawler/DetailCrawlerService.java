package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.ReviewDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DetailCrawlerService {

    private final SteamWebApiClient apiClient;
    private final AppReviewsParser reviewsParser;
    private final DetailUpsertRepository upsert;
    private final JdbcTemplate jdbc;
    private final CrawlerProperties props;

    public DetailCrawlerService(SteamWebApiClient apiClient, AppReviewsParser reviewsParser,
                                DetailUpsertRepository upsert, JdbcTemplate jdbc, CrawlerProperties props) {
        this.apiClient = apiClient;
        this.reviewsParser = reviewsParser;
        this.upsert = upsert;
        this.jdbc = jdbc;
        this.props = props;
    }

    // 응답 이상이나 요청 실패가 이만큼 이어지면 남은 게임을 부르지 않고 멈춤
    // 429 재시도(30, 60, 120초)를 게임마다 다 쓰면 한 실행이 며칠 걸려 6시간마다 도는 다음 실행과 겹치기 때문
    static final int CONSECUTIVE_BAD_LIMIT = 20;
    // 카탈로그는 리뷰 50개 이상인 게임만 담아 정상이면 거의 모든 게임에 본문이 있음, 이만큼 돌고도 본문이 0이면 요청 계약이 바뀐 것으로 봄
    static final int NO_TEXT_CHECK_MIN = 20;

    // 결과를 넷으로 나눠 셈, 처리와 실패 둘로만 세면 응답 이상이 "처리"에 섞여 수집이 멈춰도 실패 0 으로 보임
    // withText 본문을 받아 저장함, noText 정상 응답인데 본문이 없음, malformed 응답 모양이 다름, failed 요청 자체가 예외
    // aborted 는 연속 실패로 남은 게임을 부르지 않고 멈췄다는 뜻
    public record DetailResult(int withText, int noText, int malformed, int failed, boolean aborted) {
        public int processed() { return withText + noText; }

        public int total() { return withText + noText + malformed + failed; }

        // 실행 전체를 실패로 남길 이유, 정상이면 null
        // 응답 이상과 요청 실패가 정확히 절반이면 실패가 아님
        public String failureReason() {
            if (aborted) return "연속 " + CONSECUTIVE_BAD_LIMIT + "건이 응답 이상이거나 요청 실패라 중단함";
            if (total() > 0 && (malformed + failed) * 2 > total()) return "응답 이상과 요청 실패가 절반을 넘음";
            if (total() >= NO_TEXT_CHECK_MIN && withText == 0) return "본문을 하나도 받지 못함";
            return null;
        }

        public boolean broken() { return failureReason() != null; }
    }

    public DetailResult runDetailBatch() {
        Long logId = startCrawlLog();
        List<Long> appids = upsert.selectNextToCrawl(props.getDailyDetailBatchSize());
        int withText = 0, noText = 0, malformed = 0, failed = 0, consecutiveBad = 0;
        boolean aborted = false;

        for (Long appid : appids) {
            if (consecutiveBad >= CONSECUTIVE_BAD_LIMIT) {
                // 부르지 않은 게임은 수집 시각을 찍지 않아 다음 실행에서 먼저 다시 고름
                aborted = true;
                break;
            }
            int badBefore = malformed + failed;
            try {
                // 태그와 게임 정보는 catalog 배치가 맡고 여기서는 리뷰 본문만 받음
                Optional<ReviewDetail> detail = reviewsParser.parse(
                        appid, apiClient.fetchAppReviews(appid), props.getReviewSampleMaxChars());
                if (detail.isEmpty()) {
                    malformed++;
                } else if (detail.get().positiveText() == null && detail.get().negativeText() == null) {
                    // 본문이 없는 응답으로 기존 본문을 지우지 않도록 저장하지 않음
                    noText++;
                } else {
                    upsert.upsertReviewDetail(detail.get());
                    withText++;
                }
            } catch (Exception e) {
                failed++;
                // 요청 제한 같은 묶음 실패에서 게임마다 스택이 반복되므로 한 줄만 남김
                log.warn("detail crawl failed for appid {} — skipping: {}", appid, e.toString());
            } finally {
                consecutiveBad = (malformed + failed > badBefore) ? consecutiveBad + 1 : 0;
                // 성공/실패 모두 큐 뒤로 회전시켜 poison appid 가 큐 헤드를 막지 않도록 함
                try { upsert.markCrawled(appid, OffsetDateTime.now()); }
                catch (Exception e) { log.warn("markCrawled failed for appid {}: {}", appid, e.getMessage()); }
                // 실패가 이어질 때 쉬지 않고 연달아 부르지 않도록 결과와 상관없이 간격을 둠
                sleep(props.getDetailDelayMs());
            }
        }

        DetailResult result = new DetailResult(withText, noText, malformed, failed, aborted);
        String note = note(result);
        finishCrawlLog(logId, result.processed(), malformed + failed, note);
        if (result.broken()) {
            log.error("detail rolling batch broken: {}", note);
        } else {
            log.info("detail rolling batch done: {}", note);
        }
        return result;
    }

    // crawl_log.note 형식, 대시보드가 이 키 이름으로 읽으므로 바꿀 때 함께 고침
    // 실패로 남길 때는 다른 배치와 같이 FAILED: 로 시작함
    static String note(DetailResult r) {
        String counts = "withText=" + r.withText() + " noText=" + r.noText()
                + " malformed=" + r.malformed() + " failed=" + r.failed();
        return r.broken() ? "FAILED: " + r.failureReason() + " " + counts : counts;
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
