package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.support.PostgresTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
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
class CatalogSyncIntegrationTest extends PostgresTestBase {

    static MockWebServer server;

    static final String TAGS = "{\"response\":{\"tags\":[{\"tagid\":1,\"name\":\"CatSyncTagA\"},"
            + "{\"tagid\":2,\"name\":\"CatSyncTagB\"},{\"tagid\":19,\"name\":\"Action\"}]}}";

    static final String PAGE0 = "{\"response\":{\"metadata\":{\"total_matching_records\":3},\"store_items\":["
            + "{\"appid\":880001,\"name\":\"Hit Game\",\"tags\":[{\"tagid\":1,\"weight\":500},{\"tagid\":19,\"weight\":400}],"
            + "\"reviews\":{\"summary_filtered\":{\"review_count\":1200,\"percent_positive\":91,\"review_score_label\":\"Very Positive\"}},"
            + "\"basic_info\":{\"short_description\":\"new desc\",\"developers\":[{\"name\":\"Dev\"}],\"publishers\":[{\"name\":\"Pub\"}]},"
            + "\"supported_languages\":[{\"elanguage\":4,\"supported\":true}]},"
            + "{\"appid\":880002,\"name\":\"Tiny Game\",\"tags\":[{\"tagid\":2,\"weight\":5}],"
            + "\"reviews\":{\"summary_filtered\":{\"review_count\":10}}}]}}";

    static final String PAGE1 = "{\"response\":{\"metadata\":{\"total_matching_records\":3},\"store_items\":["
            + "{\"appid\":880003,\"name\":\"Tail Game\",\"reviews\":{\"summary_filtered\":{\"review_count\":3}}}]}}";

    static final String END_PAGE = "{\"response\":{\"metadata\":{\"total_matching_records\":3}}}";

    static final ObjectMapper MAPPER = new ObjectMapper();

    // input_json 의 /query/start 를 Jackson 으로 읽고 못 읽으면 -1 을 돌려줌
    static int queryStart(String inputJson) {
        try {
            return MAPPER.readTree(inputJson).at("/query/start").asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    @BeforeAll
    static void start() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @NotNull @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                HttpUrl url = request.getRequestUrl();
                String path = url == null ? "" : url.encodedPath();
                String body;
                if (path.startsWith("/IStoreService/GetTagList")) {
                    body = TAGS;
                } else if (path.startsWith("/IStoreQueryService/Query")) {
                    // 기준을 넘는 게임이 없는 페이지가 먼저 오고, 그 다음 페이지에서 저장한 뒤 전체 개수를 넘으면 끝남
                    int start = queryStart(url.queryParameter("input_json"));
                    body = start == 0 ? PAGE1 : start == 1000 ? PAGE0 : END_PAGE;
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
        registry.add("app.crawler.steam-api-base-url", () -> server.url("").toString().replaceAll("/$", ""));
        registry.add("app.crawler.catalog-page-size", () -> "1000");
        registry.add("app.crawler.catalog-call-delay-ms", () -> "0");
        registry.add("app.crawler.catalog-retry-backoff-ms", () -> "0,0,0");
    }

    @Autowired CatalogSyncService service;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM game_tags WHERE game_id IN (SELECT id FROM games WHERE appid BETWEEN 880001 AND 880003)");
        jdbc.update("DELETE FROM reviews WHERE appid BETWEEN 880001 AND 880003");
        jdbc.update("DELETE FROM games WHERE appid BETWEEN 880001 AND 880003");
        jdbc.update("DELETE FROM tags WHERE name IN ('CatSyncTagA','CatSyncTagB')");
        jdbc.update("DELETE FROM crawl_log WHERE batch_type = 'steam_catalog'");
    }

    @Test
    void syncsQualifyingGames_keepsReviewText_andEndsAtTotal() {
        // detail 이 이미 받아 둔 본문이 있는 게임을 미리 넣어 둠
        jdbc.update("INSERT INTO games (appid, name) VALUES (880001, 'Old Name')");
        jdbc.update("INSERT INTO reviews (appid, total_reviews, positive_review_text) VALUES (880001, 1, 'keep this')");

        CatalogSyncService.SyncResult r = service.runSync();

        assertThat(r.stopReason()).isEqualTo(CatalogSyncService.StopReason.END_OF_RESULTS);
        assertThat(r.saved()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM games WHERE appid=880001", String.class)).isEqualTo("Hit Game");
        assertThat(jdbc.queryForObject("SELECT genres FROM games WHERE appid=880001", String.class)).isEqualTo("Action");
        assertThat(jdbc.queryForObject("SELECT korean_support FROM games WHERE appid=880001", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT total_reviews FROM reviews WHERE appid=880001", Integer.class)).isEqualTo(1200);
        assertThat(jdbc.queryForObject("SELECT positive_review_text FROM reviews WHERE appid=880001", String.class))
                .isEqualTo("keep this");
        assertThat(jdbc.queryForObject("""
                SELECT gt.votes FROM game_tags gt JOIN tags t ON t.id=gt.tag_id JOIN games g ON g.id=gt.game_id
                WHERE g.appid=880001 AND t.name='CatSyncTagA'""", Integer.class)).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM games WHERE appid IN (880002, 880003)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT note FROM crawl_log WHERE batch_type='steam_catalog' AND finished_at IS NOT NULL", String.class))
                .isEqualTo("pages=3 stop=end-of-results skipped=0 unknownTags=0");
    }
}
