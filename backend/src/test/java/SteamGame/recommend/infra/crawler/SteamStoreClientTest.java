package SteamGame.recommend.infra.crawler;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class SteamStoreClientTest {

    MockWebServer server;
    SteamStoreClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        CrawlerProperties props = new CrawlerProperties();
        props.setStoreBaseUrl(server.url("").toString().replaceAll("/$", ""));
        client = new SteamStoreClient(WebClient.builder(), props);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fetchAppReviews_callsAppreviewsPathWithAppid_andReturnsBody() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"success\":1}"));

        String body = client.fetchAppReviews(570L);

        assertThat(body).contains("\"success\":1");
        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).startsWith("/appreviews/570");
        assertThat(req.getPath()).contains("json=1");
        assertThat(req.getPath()).contains("language=english");
    }
}
