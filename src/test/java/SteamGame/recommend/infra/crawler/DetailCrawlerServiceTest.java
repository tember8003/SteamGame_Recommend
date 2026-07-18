package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.AppTag;
import SteamGame.recommend.infra.crawler.dto.ReviewDetail;
import SteamGame.recommend.infra.crawler.dto.StorefrontDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DetailCrawlerServiceTest {

    final SteamSpyClient spyClient = mock(SteamSpyClient.class);
    final SteamStoreClient storeClient = mock(SteamStoreClient.class);
    final SteamSpyAppDetailsParser tagsParser = mock(SteamSpyAppDetailsParser.class);
    final StorefrontParser storefrontParser = mock(StorefrontParser.class);
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

    DetailCrawlerService service(CrawlerProperties p) {
        return new DetailCrawlerService(spyClient, storeClient, tagsParser, storefrontParser, reviewsParser, upsert, jdbc, p);
    }

    @Test
    void runDetailBatch_enrichesEachGame_andMarksCrawled() {
        when(upsert.selectNextToCrawl(10)).thenReturn(List.of(570L));
        when(spyClient.fetchAppDetails(570L)).thenReturn("spy");
        when(storeClient.fetchAppDetails(570L)).thenReturn("store");
        when(storeClient.fetchAppReviews(570L)).thenReturn("rev");
        when(tagsParser.parseTags("spy")).thenReturn(List.of(new AppTag("MOBA", 5)));
        when(storefrontParser.parse(570L, "store")).thenReturn(Optional.of(
                new StorefrontDetail(570L, "Dota 2", "d", "i", true, true, "Action", null, LocalDate.of(2013,7,9), "Valve", "Valve")));
        when(reviewsParser.parse(eq(570L), eq("rev"), anyInt())).thenReturn(Optional.of(
                new ReviewDetail(570L, "Very Positive", 100, new BigDecimal("80.00"), "g", "b")));

        DetailCrawlerService.DetailResult result = service(props()).runDetailBatch();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
        verify(upsert).upsertTags(570L, List.of(new AppTag("MOBA", 5)));
        verify(upsert).upsertStorefront(any(StorefrontDetail.class));
        verify(upsert).upsertReviewDetail(any(ReviewDetail.class));
        verify(upsert).markCrawled(eq(570L), any(OffsetDateTime.class));
    }

    @Test
    void perGameFailure_isIsolated_stillMarksCrawled_andCounted() {
        when(upsert.selectNextToCrawl(10)).thenReturn(List.of(1L, 2L));
        when(spyClient.fetchAppDetails(1L)).thenThrow(new RuntimeException("steamspy 500"));
        // appid 2 succeeds with empty enrichment
        when(spyClient.fetchAppDetails(2L)).thenReturn("spy");
        when(tagsParser.parseTags("spy")).thenReturn(List.of());
        when(storeClient.fetchAppDetails(2L)).thenReturn("store");
        when(storefrontParser.parse(2L, "store")).thenReturn(Optional.empty());
        when(storeClient.fetchAppReviews(2L)).thenReturn("rev");
        when(reviewsParser.parse(eq(2L), eq("rev"), anyInt())).thenReturn(Optional.empty());

        DetailCrawlerService.DetailResult result = service(props()).runDetailBatch();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.processed()).isEqualTo(1);
        verify(upsert).markCrawled(eq(1L), any());   // poison game still rotated
        verify(upsert).markCrawled(eq(2L), any());
    }

    @Test
    void emptyQueue_writesCrawlLog_andReturnsZero() {
        when(upsert.selectNextToCrawl(10)).thenReturn(List.of());
        DetailCrawlerService.DetailResult result = service(props()).runDetailBatch();
        assertThat(result.processed()).isEqualTo(0);
        assertThat(result.failed()).isEqualTo(0);
    }

    @Test
    void emptyParsedTags_doesNotWipeExistingTags_butStillMarksCrawledAndCounted() {
        when(upsert.selectNextToCrawl(10)).thenReturn(List.of(730L));
        when(spyClient.fetchAppDetails(730L)).thenReturn("spy");
        when(tagsParser.parseTags("spy")).thenReturn(List.of());
        when(storeClient.fetchAppDetails(730L)).thenReturn("store");
        when(storefrontParser.parse(730L, "store")).thenReturn(Optional.empty());
        when(storeClient.fetchAppReviews(730L)).thenReturn("rev");
        when(reviewsParser.parse(eq(730L), eq("rev"), anyInt())).thenReturn(Optional.empty());

        DetailCrawlerService.DetailResult result = service(props()).runDetailBatch();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
        verify(upsert, never()).upsertTags(anyLong(), any());
        verify(upsert).markCrawled(eq(730L), any(OffsetDateTime.class));
    }
}
