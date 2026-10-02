package SteamGame.recommend.infra.crawler;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogSyncRunnerTest {

    final CatalogSyncService service = mock(CatalogSyncService.class);
    final TagCooccurrenceRefresher refresher = mock(TagCooccurrenceRefresher.class);
    final ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    final CatalogSyncRunner runner = new CatalogSyncRunner(service, refresher, context);

    @Test
    void run_rethrows_whenSyncFails_andDoesNotRefreshOrCloseContext() {
        IllegalStateException boom = new IllegalStateException("sync broke");
        when(service.runSync()).thenThrow(boom);

        assertThatThrownBy(() -> runner.run(null)).isSameAs(boom);
        verify(refresher, never()).refresh();
        verify(context, never()).close();
    }

    @Test
    void run_rethrows_whenRefreshFails_afterSuccessfulSync() {
        when(service.runSync()).thenReturn(
                new CatalogSyncService.SyncResult(3, 10, 0, CatalogSyncService.StopReason.END_OF_RESULTS));
        IllegalStateException boom = new IllegalStateException("refresh broke");
        when(refresher.refresh()).thenThrow(boom);

        assertThatThrownBy(() -> runner.run(null)).isSameAs(boom);
        verify(context, never()).close();
    }
}
