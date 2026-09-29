package SteamGame.recommend.infra.crawler;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogSyncRunnerTest {

    @Test
    void run_rethrows_whenSyncFails_andDoesNotCloseContext() {
        CatalogSyncService service = mock(CatalogSyncService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        IllegalStateException boom = new IllegalStateException("sync broke");
        when(service.runSync()).thenThrow(boom);

        CatalogSyncRunner runner = new CatalogSyncRunner(service, context);

        assertThatThrownBy(() -> runner.run(null)).isSameAs(boom);
        verify(context, never()).close();
    }
}
