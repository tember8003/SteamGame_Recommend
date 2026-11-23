package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.ReviewDetail;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DetailCrawlerServiceTest {

    final SteamWebApiClient apiClient = mock(SteamWebApiClient.class);
    final AppReviewsParser reviewsParser = mock(AppReviewsParser.class);
    final DetailUpsertRepository upsert = mock(DetailUpsertRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    CrawlerProperties props() {
        CrawlerProperties p = new CrawlerProperties();
        p.setDetailDelayMs(0);
        p.setDailyDetailBatchSize(100);
        p.setReviewSampleMaxChars(2000);
        return p;
    }

    DetailCrawlerService service() {
        return new DetailCrawlerService(apiClient, reviewsParser, upsert, jdbc, props());
    }

    ReviewDetail detail(long appid, String pos, String neg) {
        return new ReviewDetail(appid, "Very Positive", 100, new BigDecimal("80.00"), pos, neg);
    }

    List<Long> appids(int n) {
        return LongStream.rangeClosed(1, n).boxed().toList();
    }

    DetailCrawlerService.DetailResult result(int withText, int noText, int malformed, int failed) {
        return new DetailCrawlerService.DetailResult(withText, noText, malformed, failed, false);
    }

    @Test
    void reviewWithText_isSaved_andCountedAsWithText() {
        when(upsert.selectNextToCrawl(100)).thenReturn(List.of(570L));
        when(apiClient.fetchAppReviews(570L)).thenReturn("rev");
        when(reviewsParser.parse(eq(570L), eq("rev"), anyInt())).thenReturn(Optional.of(detail(570L, "g", "b")));

        DetailCrawlerService.DetailResult result = service().runDetailBatch();

        assertThat(result.withText()).isEqualTo(1);
        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(upsert).upsertReviewDetail(any(ReviewDetail.class));
        verify(upsert).markCrawled(eq(570L), any(OffsetDateTime.class));
    }

    // 본문이 하나도 없는 정상 응답은 기존 본문을 지우지 않도록 저장하지 않는다
    @Test
    void reviewWithoutText_isNotSaved_andCountedAsNoText() {
        when(upsert.selectNextToCrawl(100)).thenReturn(List.of(7L));
        when(apiClient.fetchAppReviews(7L)).thenReturn("rev");
        when(reviewsParser.parse(eq(7L), eq("rev"), anyInt())).thenReturn(Optional.of(detail(7L, null, null)));

        DetailCrawlerService.DetailResult result = service().runDetailBatch();

        assertThat(result.noText()).isEqualTo(1);
        assertThat(result.processed()).isEqualTo(1);
        verify(upsert, never()).upsertReviewDetail(any());
        verify(upsert).markCrawled(eq(7L), any());
    }

    @Test
    void malformedResponse_isCountedSeparately_andNotSaved() {
        when(upsert.selectNextToCrawl(100)).thenReturn(List.of(8L));
        when(apiClient.fetchAppReviews(8L)).thenReturn("<html>");
        when(reviewsParser.parse(eq(8L), eq("<html>"), anyInt())).thenReturn(Optional.empty());

        DetailCrawlerService.DetailResult result = service().runDetailBatch();

        assertThat(result.malformed()).isEqualTo(1);
        assertThat(result.processed()).isZero();
        verify(upsert, never()).upsertReviewDetail(any());
        verify(upsert).markCrawled(eq(8L), any());
    }

    @Test
    void perGameFailure_isIsolated_stillMarksCrawled_andCounted() {
        when(upsert.selectNextToCrawl(100)).thenReturn(List.of(1L, 2L));
        when(apiClient.fetchAppReviews(1L)).thenThrow(new RuntimeException("steam 429"));
        when(apiClient.fetchAppReviews(2L)).thenReturn("rev");
        when(reviewsParser.parse(eq(2L), eq("rev"), anyInt())).thenReturn(Optional.of(detail(2L, "g", null)));

        DetailCrawlerService.DetailResult result = service().runDetailBatch();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.withText()).isEqualTo(1);
        verify(upsert).markCrawled(eq(1L), any());
        verify(upsert).markCrawled(eq(2L), any());
    }

    @Test
    void emptyQueue_returnsZero_andIsNotBroken() {
        when(upsert.selectNextToCrawl(100)).thenReturn(List.of());
        DetailCrawlerService.DetailResult result = service().runDetailBatch();
        assertThat(result.processed()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.broken()).isFalse();
    }

    // 응답 이상과 요청 실패가 절반을 넘으면 실행 전체를 실패로 남긴다, 경계는 정확히 절반이면 실패가 아니다
    @Test
    void broken_whenMalformedPlusFailedExceedHalf() {
        assertThat(result(1, 0, 1, 0).broken()).isFalse();
        assertThat(result(1, 0, 1, 1).broken()).isTrue();
        assertThat(result(0, 1, 0, 2).broken()).isTrue();
        assertThat(result(5, 5, 0, 0).broken()).isFalse();
    }

    // 모두 정상 응답인데 본문이 하나도 없으면 요청 계약이 바뀐 것으로 본다, 기준 수 미만의 작은 실행은 판정하지 않는다
    @Test
    void broken_whenNoTextAtAllOverEnoughGames() {
        int min = DetailCrawlerService.NO_TEXT_CHECK_MIN;
        assertThat(result(0, min, 0, 0).broken()).isTrue();
        assertThat(result(0, min - 1, 0, 0).broken()).isFalse();
        assertThat(result(1, min, 0, 0).broken()).isFalse();
    }

    @Test
    void broken_whenAborted() {
        assertThat(new DetailCrawlerService.DetailResult(10, 0, 0, 0, true).broken()).isTrue();
    }

    @Test
    void note_listsEveryCount_andStartsWithFailedWithReasonWhenBroken() {
        assertThat(DetailCrawlerService.note(result(3, 2, 1, 0)))
                .isEqualTo("withText=3 noText=2 malformed=1 failed=0");
        assertThat(DetailCrawlerService.note(result(0, 0, 9, 1)))
                .startsWith("FAILED: 응답 이상과 요청 실패가 절반을 넘음 ")
                .endsWith("withText=0 noText=0 malformed=9 failed=1");
    }

    @Test
    void crawlLog_recordsValidResponsesAsProcessed_andBrokenOnesAsFailed() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(42L);
        when(upsert.selectNextToCrawl(100)).thenReturn(List.of(1L, 2L, 3L));
        when(apiClient.fetchAppReviews(anyLong())).thenReturn("rev");
        when(reviewsParser.parse(eq(1L), anyString(), anyInt())).thenReturn(Optional.of(detail(1L, "g", null)));
        when(reviewsParser.parse(eq(2L), anyString(), anyInt())).thenReturn(Optional.of(detail(2L, null, null)));
        when(reviewsParser.parse(eq(3L), anyString(), anyInt())).thenReturn(Optional.empty());

        service().runDetailBatch();

        verify(jdbc).update(contains("UPDATE crawl_log"), any(OffsetDateTime.class), eq(2), eq(1),
                eq("withText=1 noText=1 malformed=1 failed=0"), eq(42L));
    }

    // 2026-10 상점 주소 폐기 때처럼 모든 응답이 HTML 이면 첫 실행의 crawl_log 가 FAILED 로 남아야 한다
    @Test
    void crawlLog_noteStartsWithFailed_whenRunIsBroken() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(42L);
        when(upsert.selectNextToCrawl(100)).thenReturn(appids(5));
        when(apiClient.fetchAppReviews(anyLong())).thenReturn("<html>");
        when(reviewsParser.parse(anyLong(), anyString(), anyInt())).thenReturn(Optional.empty());

        service().runDetailBatch();

        verify(jdbc).update(contains("UPDATE crawl_log"), any(OffsetDateTime.class), eq(0), eq(5),
                startsWith("FAILED: "), eq(42L));
    }

    // 연속 실패가 기준에 닿으면 남은 게임을 부르지 않고, 부르지 않은 게임은 수집 시각도 찍지 않는다
    @Test
    void stopsAfterConsecutiveBadResults_andLeavesTheRestUntouched() {
        int limit = DetailCrawlerService.CONSECUTIVE_BAD_LIMIT;
        when(upsert.selectNextToCrawl(100)).thenReturn(appids(limit + 10));
        when(apiClient.fetchAppReviews(anyLong())).thenThrow(new RuntimeException("steam 429"));

        DetailCrawlerService.DetailResult result = service().runDetailBatch();

        assertThat(result.aborted()).isTrue();
        assertThat(result.failed()).isEqualTo(limit);
        assertThat(result.broken()).isTrue();
        verify(apiClient, times(limit)).fetchAppReviews(anyLong());
        verify(upsert, times(limit)).markCrawled(anyLong(), any());
        verify(upsert, never()).markCrawled(eq((long) limit + 1), any());
    }

    // 중간에 정상 결과가 하나라도 끼면 연속 횟수는 처음부터 다시 센다
    @Test
    void goodResultResetsConsecutiveBadCount() {
        int limit = DetailCrawlerService.CONSECUTIVE_BAD_LIMIT;
        List<Long> ids = appids(limit * 2);
        long goodId = limit;   // limit-1 번 실패한 뒤 한 번 성공
        when(upsert.selectNextToCrawl(100)).thenReturn(ids);
        when(apiClient.fetchAppReviews(anyLong())).thenReturn("rev");
        when(reviewsParser.parse(anyLong(), anyString(), anyInt())).thenReturn(Optional.empty());
        when(reviewsParser.parse(eq(goodId), anyString(), anyInt())).thenReturn(Optional.of(detail(goodId, "g", null)));

        DetailCrawlerService.DetailResult result = service().runDetailBatch();

        assertThat(result.aborted()).isFalse();
        verify(apiClient, times(limit * 2)).fetchAppReviews(anyLong());
    }
}
