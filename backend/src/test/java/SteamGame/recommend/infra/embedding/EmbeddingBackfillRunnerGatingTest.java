package SteamGame.recommend.infra.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmbeddingBackfillRunnerGatingTest {

    @Configuration
    static class StubBeans {
        @Bean EmbeddingBackfillService embeddingBackfillService() { return mock(EmbeddingBackfillService.class); }
    }

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StubBeans.class)
            .withUserConfiguration(EmbeddingBackfillRunner.class);

    @Test
    void runnerBeanAbsent_whenPropertyUnset() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(EmbeddingBackfillRunner.class));
    }

    @Test
    void runnerBeanPresent_whenRunIsEmbed() {
        runner.withPropertyValues("app.crawler.run=embed")
                .run(ctx -> assertThat(ctx).hasSingleBean(EmbeddingBackfillRunner.class));
    }
}
