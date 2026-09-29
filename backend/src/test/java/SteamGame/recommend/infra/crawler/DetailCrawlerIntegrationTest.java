package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.support.PostgresTestBase;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
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

@SpringBootTest(properties = {"steam.api.key=test", "spring.ai.google.api-key=test"})
class DetailCrawlerIntegrationTest extends PostgresTestBase {

    static MockWebServer server;
    static final long APPID = 570L;

    @BeforeAll
    static void start() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @NotNull @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (!path.startsWith("/appreviews/")) return new MockResponse().setResponseCode(404);
                String body = "{\"success\":1,\"query_summary\":{\"review_score_desc\":\"Very Positive\","
                        + "\"total_positive\":80,\"total_negative\":20,\"total_reviews\":100},"
                        + "\"reviews\":[{\"review\":\"great\",\"voted_up\":true},{\"review\":\"bad\",\"voted_up\":false}]}";
                return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
            }
        });
        server.start();
    }

    @AfterAll
    static void stop() throws Exception { server.shutdown(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.crawler.store-base-url", () -> server.url("").toString().replaceAll("/$", ""));
        registry.add("app.crawler.detail-delay-ms", () -> "0");
        registry.add("app.crawler.daily-detail-batch-size", () -> "50");
    }

    @Autowired DetailCrawlerService service;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM reviews WHERE appid=?", APPID);
        jdbc.update("DELETE FROM games WHERE appid=?", APPID);
    }

    @Test
    void savesReviewText_keepsCatalogSummary_andStampsLastCrawled() {
        jdbc.update("INSERT INTO games (appid, name, review_count) VALUES (?, 'Dota 2', 999999)", APPID);
        jdbc.update("INSERT INTO reviews (appid, total_reviews, review_score) VALUES (?, 1450000, 'Overwhelmingly Positive')", APPID);

        DetailCrawlerService.DetailResult result = service.runDetailBatch();

        assertThat(result.processed()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT positive_review_text FROM reviews WHERE appid=?", String.class, APPID)).isEqualTo("great");
        assertThat(jdbc.queryForObject("SELECT negative_review_text FROM reviews WHERE appid=?", String.class, APPID)).isEqualTo("bad");
        assertThat(jdbc.queryForObject("SELECT total_reviews FROM reviews WHERE appid=?", Integer.class, APPID)).isEqualTo(1450000);
        assertThat(jdbc.queryForObject("SELECT review_score FROM reviews WHERE appid=?", String.class, APPID))
                .isEqualTo("Overwhelmingly Positive");
        assertThat(jdbc.queryForObject("SELECT last_crawled_at FROM games WHERE appid=?", java.sql.Timestamp.class, APPID)).isNotNull();
    }
}
