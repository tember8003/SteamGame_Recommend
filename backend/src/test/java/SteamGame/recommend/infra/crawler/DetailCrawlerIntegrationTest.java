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
    // true 면 2026-10 폐기된 상점 주소처럼 모든 요청에 HTML 을 돌려준다
    static volatile boolean htmlMode = false;

    @BeforeAll
    static void start() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @NotNull @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (!path.startsWith("/IUserReviewsService/GetAppReviews/v1/")) return new MockResponse().setResponseCode(404);
                if (htmlMode) {
                    return new MockResponse().setHeader("Content-Type", "text/html; charset=UTF-8")
                            .setBody("<!DOCTYPE html><html><head><title>Steam</title></head><body></body></html>");
                }
                String body = "{\"response\":{\"query_summary\":{\"review_score_desc\":\"Very Positive\","
                        + "\"total_positive\":80,\"total_negative\":20,\"total_reviews\":100},"
                        + "\"reviews\":[{\"review\":\"great\",\"voted_up\":true},{\"review\":\"bad\",\"voted_up\":false}]}}";
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
        registry.add("app.crawler.detail-delay-ms", () -> "0");
        registry.add("app.crawler.daily-detail-batch-size", () -> "50");
    }

    @Autowired DetailCrawlerService service;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        htmlMode = false;
        jdbc.update("DELETE FROM reviews WHERE appid=?", APPID);
        jdbc.update("DELETE FROM games WHERE appid=?", APPID);
    }

    @Test
    void savesReviewText_keepsCatalogSummary_andStampsLastCrawled() {
        jdbc.update("INSERT INTO games (appid, name, review_count) VALUES (?, 'Dota 2', 999999)", APPID);
        jdbc.update("INSERT INTO reviews (appid, total_reviews, review_score) VALUES (?, 1450000, 'Overwhelmingly Positive')", APPID);

        DetailCrawlerService.DetailResult result = service.runDetailBatch();

        assertThat(result.withText()).isGreaterThanOrEqualTo(1);
        assertThat(result.malformed()).isZero();
        assertThat(jdbc.queryForObject("SELECT text_updated_at FROM reviews WHERE appid=?", java.sql.Timestamp.class, APPID)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT positive_review_text FROM reviews WHERE appid=?", String.class, APPID)).isEqualTo("great");
        assertThat(jdbc.queryForObject("SELECT negative_review_text FROM reviews WHERE appid=?", String.class, APPID)).isEqualTo("bad");
        assertThat(jdbc.queryForObject("SELECT total_reviews FROM reviews WHERE appid=?", Integer.class, APPID)).isEqualTo(1450000);
        assertThat(jdbc.queryForObject("SELECT review_score FROM reviews WHERE appid=?", String.class, APPID))
                .isEqualTo("Overwhelmingly Positive");
        assertThat(jdbc.queryForObject("SELECT last_crawled_at FROM games WHERE appid=?", java.sql.Timestamp.class, APPID)).isNotNull();
    }

    // 사고 재현: 실제 WebClient 를 거쳐 HTML 을 받으면 "리뷰 없음"으로 삼키지 않고 응답 이상으로 세고 실행을 실패로 남긴다
    @Test
    void htmlResponses_areCountedAsMalformed_andRunIsRecordedAsFailed() {
        htmlMode = true;
        jdbc.update("INSERT INTO games (appid, name, review_count) VALUES (?, 'Dota 2', 999999)", APPID);
        jdbc.update("INSERT INTO reviews (appid, total_reviews, positive_review_text) VALUES (?, 1450000, 'kept')", APPID);

        DetailCrawlerService.DetailResult result = service.runDetailBatch();

        assertThat(result.malformed()).isGreaterThanOrEqualTo(1);
        assertThat(result.withText()).isZero();
        assertThat(result.broken()).isTrue();
        assertThat(jdbc.queryForObject("SELECT positive_review_text FROM reviews WHERE appid=?", String.class, APPID)).isEqualTo("kept");
        assertThat(jdbc.queryForObject("SELECT text_updated_at FROM reviews WHERE appid=?", java.sql.Timestamp.class, APPID)).isNull();
        String note = jdbc.queryForObject(
                "SELECT note FROM crawl_log WHERE batch_type = 'detail_rolling' ORDER BY id DESC LIMIT 1", String.class);
        assertThat(note).startsWith("FAILED: ");
    }
}
