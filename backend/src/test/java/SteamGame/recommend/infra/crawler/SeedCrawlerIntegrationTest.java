package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.support.PostgresTestBase;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "steam.api.key=test",
        "spring.ai.google.api-key=test"
})
class SeedCrawlerIntegrationTest extends PostgresTestBase {

    static MockWebServer steamSpy;

    @BeforeAll
    static void startServer() throws Exception {
        steamSpy = new MockWebServer();
        steamSpy.start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        steamSpy.shutdown();
    }

    @DynamicPropertySource
    static void crawlerProps(DynamicPropertyRegistry registry) {
        registry.add("app.crawler.steamspy-base-url", () -> steamSpy.url("/api.php").toString());
        registry.add("app.crawler.page-delay-ms", () -> "0");
        registry.add("app.crawler.min-reviews", () -> "50");
        registry.add("app.crawler.max-pages", () -> "100");
    }

    @Autowired SeedCrawlerService service;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanupSeededRows() {
        jdbc.update("DELETE FROM reviews WHERE appid IN (570, 999999)");
        jdbc.update("DELETE FROM games WHERE appid IN (570, 999999)");
    }

    @Test
    void seed_populatesQualifyingGamesAndReviews_writesCrawlLog_idempotent() {
        enqueuePages();

        SeedCrawlerService.SeedResult first = service.runSeed();

        assertThat(first.gamesUpserted()).isEqualTo(1);   // 리뷰 적은 게임 제외
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM games WHERE appid=570", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM games WHERE appid=999999", Integer.class)).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT review_count FROM games WHERE appid=570", Integer.class)).isEqualTo(1450000);
        assertThat(jdbc.queryForObject("SELECT total_reviews FROM reviews WHERE appid=570", Integer.class)).isEqualTo(1450000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crawl_log WHERE batch_type='steamspy_seed' AND finished_at IS NOT NULL", Integer.class)).isEqualTo(1);

        // 멱등: 다시 돌려도 게임 행 수 1 유지
        enqueuePages();
        service.runSeed();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM games WHERE appid=570", Integer.class)).isEqualTo(1);
    }

    private void enqueuePages() {
        steamSpy.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("""
            {"570":{"appid":570,"name":"Dota 2","developer":"Valve","publisher":"Valve","positive":1200000,"negative":250000,"owners":"100,000,000 .. 200,000,000"},
             "999999":{"appid":999999,"name":"Tiny","developer":"d","publisher":"","positive":10,"negative":5,"owners":"0 .. 20,000"}}
            """));
        steamSpy.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{}"));
    }
}
