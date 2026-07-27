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
                String body;
                if (path.contains("request=appdetails")) {          // SteamSpy tags
                    body = "{\"appid\":570,\"name\":\"Dota 2\",\"tags\":{\"MOBA\":20225,\"Strategy\":14289}}";
                } else if (path.startsWith("/api/appdetails")) {     // Storefront
                    body = "{\"570\":{\"success\":true,\"data\":{\"name\":\"Dota 2\",\"short_description\":\"desc\","
                          + "\"header_image\":\"http://img\",\"is_free\":true,\"supported_languages\":\"English, Korean\","
                          + "\"genres\":[{\"description\":\"Action\"}],\"price_overview\":null,"
                          + "\"release_date\":{\"coming_soon\":false,\"date\":\"Jul 9, 2013\"},"
                          + "\"developers\":[\"Valve\"],\"publishers\":[\"Valve\"]}}}";
                } else if (path.startsWith("/appreviews/")) {        // appreviews
                    body = "{\"success\":1,\"query_summary\":{\"review_score_desc\":\"Very Positive\","
                          + "\"total_positive\":80,\"total_negative\":20,\"total_reviews\":100},"
                          + "\"reviews\":[{\"review\":\"great\",\"voted_up\":true},{\"review\":\"bad\",\"voted_up\":false}]}";
                } else {
                    return new MockResponse().setResponseCode(404);
                }
                return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
            }
        });
        server.start();
    }

    @AfterAll
    static void stop() throws Exception { server.shutdown(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        String base = server.url("").toString().replaceAll("/$", "");
        registry.add("app.crawler.steamspy-base-url", () -> base + "/api.php");
        registry.add("app.crawler.store-base-url", () -> base);
        registry.add("app.crawler.detail-delay-ms", () -> "0");
        registry.add("app.crawler.daily-detail-batch-size", () -> "50");
    }

    @Autowired DetailCrawlerService service;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM game_tags WHERE game_id IN (SELECT id FROM games WHERE appid=?)", APPID);
        jdbc.update("DELETE FROM reviews WHERE appid=?", APPID);
        jdbc.update("DELETE FROM games WHERE appid=?", APPID);
        jdbc.update("DELETE FROM tags WHERE name IN ('MOBA','Strategy')");
    }

    @Test
    void enrichesSeededGame_acrossAllThreeSources_andStampsLastCrawled() {
        // seed a bare game row (as the seed crawler would)
        jdbc.update("INSERT INTO games (appid, name) VALUES (?, 'Seed') ON CONFLICT (appid) DO NOTHING", APPID);

        DetailCrawlerService.DetailResult result = service.runDetailBatch();

        assertThat(result.processed()).isGreaterThanOrEqualTo(1);
        Long gameId = jdbc.queryForObject("SELECT id FROM games WHERE appid=?", Long.class, APPID);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_tags WHERE game_id=?", Integer.class, gameId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT votes FROM game_tags gt JOIN tags t ON t.id=gt.tag_id WHERE gt.game_id=? AND t.name='MOBA'",
                Integer.class, gameId)).isEqualTo(20225);
        assertThat(jdbc.queryForObject("SELECT name FROM games WHERE appid=?", String.class, APPID)).isEqualTo("Dota 2");
        assertThat(jdbc.queryForObject("SELECT korean_support FROM games WHERE appid=?", Boolean.class, APPID)).isTrue();
        assertThat(jdbc.queryForObject("SELECT review_score FROM reviews WHERE appid=?", String.class, APPID)).isEqualTo("Very Positive");
        assertThat(jdbc.queryForObject("SELECT positive_review_text FROM reviews WHERE appid=?", String.class, APPID)).isEqualTo("great");
        assertThat(jdbc.queryForObject("SELECT last_crawled_at FROM games WHERE appid=?", java.sql.Timestamp.class, APPID)).isNotNull();
    }
}
