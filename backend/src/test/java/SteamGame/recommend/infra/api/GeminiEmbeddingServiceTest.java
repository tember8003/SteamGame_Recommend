package SteamGame.recommend.infra.api;

import SteamGame.recommend.infra.embedding.EmbeddingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiEmbeddingServiceTest {

    MockWebServer server;
    GeminiEmbeddingService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        EmbeddingProperties props = new EmbeddingProperties();
        props.setBaseUrl(server.url("").toString().replaceAll("/$", ""));
        props.setModel("gemini-embedding-001");
        service = new GeminiEmbeddingService(new ObjectMapper(), WebClient.builder(), props, "test");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void embed_parsesValues_andSendsKeyAndEndpoint() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"embedding\":{\"values\":[0.1,0.2,0.3]}}"));

        float[] v = service.embed("hello");

        assertThat(v).containsExactly(new float[]{0.1f, 0.2f, 0.3f}, Offset.offset(1e-6f));
        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).contains(":embedContent");
        assertThat(req.getPath()).contains("key=test");
    }

    @Test
    void embedBatch_parsesEachEmbedding() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"embeddings\":[{\"values\":[0.1,0.2]},{\"values\":[0.3,0.4]}]}"));

        List<float[]> vs = service.embedBatch(List.of("a", "b"));

        assertThat(vs).hasSize(2);
        assertThat(vs.get(0)).containsExactly(new float[]{0.1f, 0.2f}, Offset.offset(1e-6f));
        assertThat(vs.get(1)).containsExactly(new float[]{0.3f, 0.4f}, Offset.offset(1e-6f));
    }

    @Test
    void embed_missingValues_returnsNull() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{}"));
        assertThat(service.embed("x")).isNull();
    }

    @Test
    void fallbacks_returnDegradedValues() {
        assertThat(service.fallbackEmbed("x", new RuntimeException("boom"))).isNull();
        assertThat(service.fallbackEmbedBatch(List.of("a"), new RuntimeException("boom"))).isEmpty();
    }
}
