package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.StoreItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogSyncServiceTest {

    final SteamWebApiClient client = mock(SteamWebApiClient.class);
    final StoreItemParser parser = mock(StoreItemParser.class);
    final CatalogUpsertRepository upsert = mock(CatalogUpsertRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    CatalogSyncService service;

    static StoreItem game(long appid, int reviews) {
        return new StoreItem(appid, "G" + appid, null, null, false, false, null, null, null, null, null,
                reviews, null, null, List.of());
    }

    @BeforeEach
    void setUp() {
        CrawlerProperties p = new CrawlerProperties();
        p.setCatalogPageSize(2);
        p.setCatalogMaxPages(5);
        p.setCatalogCallDelayMs(0);
        p.setMinReviews(50);
        service = new CatalogSyncService(client, parser, upsert, jdbc, p);
        when(client.fetchTagList()).thenReturn("tags");
        when(parser.parseTagList("tags")).thenReturn(Map.of(1, "Action"));
    }

    static StoreItemParser.QueryPage qp(int raw, List<StoreItem> items) {
        return new StoreItemParser.QueryPage(raw, items, 0, 0);
    }

    static StoreItemParser.QueryPage qp(int raw, List<StoreItem> items, int total) {
        return new StoreItemParser.QueryPage(raw, items, total, 0);
    }

    void page(int start, StoreItemParser.QueryPage result) {
        when(client.fetchQueryPage(start, 2)).thenReturn("p" + start);
        when(parser.parsePage(eq("p" + start), any())).thenReturn(result);
    }

    @Test
    void pageWithNoQualifyingGames_isSkipped_andRunContinues() {
        page(0, qp(2, List.of(game(1, 900), game(2, 800)), 5));
        page(2, qp(2, List.of(game(3, 49), game(4, 10)), 5));
        page(4, qp(1, List.of(game(5, 700)), 5));
        page(6, qp(0, List.of(), 5));

        CatalogSyncService.SyncResult r = service.runSync();

        assertThat(r.stopReason()).isEqualTo(CatalogSyncService.StopReason.END_OF_RESULTS);
        assertThat(r.pages()).isEqualTo(4);
        assertThat(r.saved()).isEqualTo(3);
        verify(upsert).upsertItem(game(5, 700));
        verify(upsert, never()).upsertItem(game(3, 49));
    }

    @Test
    void leadingPagesWithoutQualifyingGames_thenSaves_endsNormally() {
        page(0, qp(2, List.of(game(1, 3), game(2, 0)), 4));
        page(2, qp(2, List.of(game(3, 900), game(4, 800)), 4));
        page(4, qp(0, List.of(), 4));

        CatalogSyncService.SyncResult r = service.runSync();

        assertThat(r.stopReason()).isEqualTo(CatalogSyncService.StopReason.END_OF_RESULTS);
        assertThat(r.saved()).isEqualTo(2);
    }

    @Test
    void mixedPage_savesOnlyQualifying_andContinues() {
        page(0, qp(2, List.of(game(1, 60), game(2, 40)), 2));
        page(2, qp(0, List.of(), 2));

        CatalogSyncService.SyncResult r = service.runSync();

        assertThat(r.stopReason()).isEqualTo(CatalogSyncService.StopReason.END_OF_RESULTS);
        assertThat(r.saved()).isEqualTo(1);
        verify(upsert).upsertItem(game(1, 60));
        verify(upsert, never()).upsertItem(game(2, 40));
    }

    @Test
    void hittingMaxPagesBeforeTheEnd_fails() {
        for (int start = 0; start < 10; start += 2) {
            page(start, qp(2, List.of(game(start + 100, 999)), 100000));
        }

        assertThatThrownBy(() -> service.runSync())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("catalogMaxPages");
        verify(upsert, times(5)).upsertItem(any());
    }

    @Test
    void emptyTagList_abortsBeforeAnyPage() {
        when(parser.parseTagList("tags")).thenReturn(Map.of());

        assertThatThrownBy(() -> service.runSync()).isInstanceOf(IllegalStateException.class);
        verify(client, never()).fetchQueryPage(anyInt(), anyInt());
        verify(upsert, never()).upsertItem(any());
    }

    @Test
    void unparseablePage_failsInsteadOfStopping() {
        page(0, qp(2, List.of()));

        assertThatThrownBy(() -> service.runSync()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emptyFirstPage_failsInsteadOfReportingSuccess() {
        page(0, qp(0, List.of()));

        assertThatThrownBy(() -> service.runSync()).isInstanceOf(IllegalStateException.class);
        verify(upsert, never()).upsertItem(any());
    }

    @Test
    void oneGameSaveFailure_isCounted_andOthersStillSaved() {
        page(0, qp(2, List.of(game(1, 900), game(2, 800)), 2));
        page(2, qp(0, List.of(), 2));
        doThrow(new RuntimeException("db")).when(upsert).upsertItem(game(1, 900));

        CatalogSyncService.SyncResult r = service.runSync();

        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.saved()).isEqualTo(1);
        verify(upsert).upsertItem(game(2, 800));
    }

    @Test
    void apiFailureMidRun_propagates() {
        page(0, qp(2, List.of(game(1, 900))));
        when(client.fetchQueryPage(2, 2)).thenThrow(new RuntimeException("429 exhausted"));

        assertThatThrownBy(() -> service.runSync()).hasMessageContaining("429");
        verify(upsert).upsertItem(game(1, 900));
    }

    @Test
    void emptyPageAfterTotal_endsNormally() {
        page(0, qp(2, List.of(game(1, 900), game(2, 800)), 3));
        page(2, qp(1, List.of(game(3, 700)), 3));
        page(4, qp(0, List.of(), 3));

        CatalogSyncService.SyncResult r = service.runSync();

        assertThat(r.stopReason()).isEqualTo(CatalogSyncService.StopReason.END_OF_RESULTS);
        assertThat(r.saved()).isEqualTo(3);
    }

    @Test
    void emptyPageBeforeTotal_midRun_fails() {
        page(0, qp(2, List.of(game(1, 900), game(2, 800)), 100000));
        page(2, qp(0, List.of(), 100000));

        assertThatThrownBy(() -> service.runSync())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("page 1");
    }

    @Test
    void emptyPageWithNoTotal_evenAfterSaves_fails() {
        page(0, qp(2, List.of(game(1, 900), game(2, 800))));
        page(2, qp(0, List.of()));

        assertThatThrownBy(() -> service.runSync()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wholeRunWithNothingQualifying_fails() {
        page(0, qp(2, List.of(game(1, 0), game(2, 10)), 4));
        page(2, qp(2, List.of(game(3, 5), game(4, 1)), 4));
        page(4, qp(0, List.of(), 4));

        assertThatThrownBy(() -> service.runSync())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("리뷰 기준");
        verify(upsert, never()).upsertItem(any());
    }

    @Test
    void allUpsertsFailOnAPage_fails_withLastCause() {
        page(0, qp(2, List.of(game(1, 900), game(2, 800))));
        RuntimeException dbError = new RuntimeException("db down");
        doThrow(dbError).when(upsert).upsertItem(any());

        assertThatThrownBy(() -> service.runSync())
                .isInstanceOf(IllegalStateException.class)
                .hasCause(dbError);
    }

    @Test
    void crawlLogStartFailure_failsRun() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenThrow(new RuntimeException("no table"));

        assertThatThrownBy(() -> service.runSync())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("crawl_log");
        verify(client, never()).fetchTagList();
    }

    @Test
    void successNote_carriesLabelSkippedAndUnknownTags() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(7L);
        page(0, new StoreItemParser.QueryPage(3, List.of(game(1, 900), game(2, 800)), 4, 4));
        page(2, qp(2, List.of(game(3, 1), game(4, 2)), 4));
        page(4, qp(0, List.of(), 4));

        service.runSync();

        verify(jdbc).update(anyString(), any(), eq(2), eq(0),
                eq("pages=3 stop=end-of-results skipped=1 unknownTags=4"), eq(7L));
    }

    @Test
    void stopReason_labels() {
        assertThat(CatalogSyncService.StopReason.values()).hasSize(1);
        assertThat(CatalogSyncService.StopReason.END_OF_RESULTS.label()).isEqualTo("end-of-results");
    }
}
