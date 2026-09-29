package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.ReviewDetail;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DetailCrawlerServiceTest {

    final SteamStoreClient storeClient = mock(SteamStoreClient.class);
    final AppReviewsParser reviewsParser = mock(AppReviewsParser.class);
    final DetailUpsertRepository upsert = mock(DetailUpsertRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    CrawlerProperties props() {
        CrawlerProperties p = new CrawlerProperties();
        p.setDetailDelayMs(0);
        p.setDailyDetailBatchSize(10);
        p.setReviewSampleMaxChars(2000);
        return p;
    }

    DetailCrawlerService service() {
        return new DetailCrawlerService(storeClient, reviewsParser, upsert, jdbc, props());
    }

    @Test
    void runDetailBatch_savesReviewText_andMarksCrawled() {
        when(upsert.selectNextToCrawl(10)).thenReturn(List.of(570L));
        when(storeClient.fetchAppReviews(570L)).thenReturn("rev");
        when(reviewsParser.parse(eq(570L), eq("rev"), anyInt())).thenReturn(Optional.of(
                new ReviewDetail(570L, "Very Positive", 100, new BigDecimal("80.00"), "g", "b")));

        DetailCrawlerService.DetailResult result = service().runDetailBatch();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
        verify(upsert).upsertReviewDetail(any(ReviewDetail.class));
        verify(upsert).markCrawled(eq(570L), any(OffsetDateTime.class));
    }

    @Test
    void perGameFailure_isIsolated_stillMarksCrawled_andCounted() {
        when(upsert.selectNextToCrawl(10)).thenReturn(List.of(1L, 2L));
        when(storeClient.fetchAppReviews(1L)).thenThrow(new RuntimeException("store 429"));
        when(storeClient.fetchAppReviews(2L)).thenReturn("rev");
        when(reviewsParser.parse(eq(2L), eq("rev"), anyInt())).thenReturn(Optional.empty());

        DetailCrawlerService.DetailResult result = service().runDetailBatch();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.processed()).isEqualTo(1);
        verify(upsert).markCrawled(eq(1L), any());
        verify(upsert).markCrawled(eq(2L), any());
    }

    @Test
    void emptyQueue_returnsZero() {
        when(upsert.selectNextToCrawl(10)).thenReturn(List.of());
        DetailCrawlerService.DetailResult result = service().runDetailBatch();
        assertThat(result.processed()).isEqualTo(0);
        assertThat(result.failed()).isEqualTo(0);
    }
}
