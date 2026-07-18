package SteamGame.recommend.infra.crawler;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class SteamSpyClientTest {

    private MockWebServer server;
    private SteamSpyClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        CrawlerProperties props = new CrawlerProperties();
        props.setSteamspyBaseUrl(server.url("/api.php").toString());
        client = new SteamSpyClient(WebClient.builder(), props);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fetchAllPage_hitsRequestAllWithPageParam_andReturnsBody() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"570\":{\"appid\":570,\"name\":\"Dota 2\"}}"));

        String body = client.fetchAllPage(3);

        assertThat(body).contains("\"Dota 2\"");
        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getPath()).contains("request=all");
        assertThat(recorded.getPath()).contains("page=3");
    }

    @Test
    void fetchAllPage_handlesLargeBodyOver256KB() throws Exception {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 20000; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(i).append("\":{\"appid\":").append(i)
              .append(",\"name\":\"Game ").append(i).append("\"}");
        }
        sb.append("}");
        String big = sb.toString();
        assertThat(big.length()).isGreaterThan(300_000); // 기본 256KB 초과 보장

        server.enqueue(new okhttp3.mockwebserver.MockResponse()
                .setHeader("Content-Type", "application/json").setBody(big));

        String body = client.fetchAllPage(0);
        assertThat(body.length()).isEqualTo(big.length());
    }
}
