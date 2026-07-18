package SteamGame.recommend.infra.crawler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DetailCrawlerRunnerGatingTest {

    @Configuration
    static class StubBeans {
        @Bean DetailCrawlerService detailCrawlerService() { return mock(DetailCrawlerService.class); }
    }

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StubBeans.class)
            .withUserConfiguration(DetailCrawlerRunner.class);

    @Test
    void runnerBeanAbsent_whenPropertyUnset() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(DetailCrawlerRunner.class));
    }

    @Test
    void runnerBeanPresent_whenRunIsDetail() {
        runner.withPropertyValues("app.crawler.run=detail")
                .run(ctx -> assertThat(ctx).hasSingleBean(DetailCrawlerRunner.class));
    }
}
