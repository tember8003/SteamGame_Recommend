package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "steam.api.key=test",
        "spring.ai.google.api-key=test"
})
class SeedCrawlerRunnerGatingTest extends PostgresTestBase {

    @Autowired ApplicationContext ctx;

    @Test
    void seedRunner_isNotRegistered_whenCrawlerRunPropertyAbsent() {
        assertThat(ctx.getBeanNamesForType(SeedCrawlerRunner.class)).isEmpty();
    }
}
