package SteamGame.recommend.infra.crawler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CatalogSyncRunnerGatingTest {

    @Configuration
    static class StubBeans {
        @Bean CatalogSyncService catalogSyncService() { return mock(CatalogSyncService.class); }
    }

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StubBeans.class)
            .withUserConfiguration(CatalogSyncRunner.class);

    @Test
    void runnerBeanAbsent_whenPropertyUnset() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(CatalogSyncRunner.class));
    }

    @Test
    void runnerBeanAbsent_whenRunIsDetail() {
        runner.withPropertyValues("app.crawler.run=detail")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(CatalogSyncRunner.class));
    }

    @Test
    void runnerBeanPresent_whenRunIsCatalog() {
        runner.withPropertyValues("app.crawler.run=catalog")
                .run(ctx -> assertThat(ctx).hasSingleBean(CatalogSyncRunner.class));
    }
}
