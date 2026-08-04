package SteamGame.recommend.infra.llm;

import SteamGame.recommend.config.StringCleanerSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GroqChatProviderTest {

    MockWebServer server;
    String url;
    ObjectMapper objectMapper;
    GroqChatProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        objectMapper = new ObjectMapper();
        url = server.url("").toString().replaceAll("/$", "");
        provider = new GroqChatProvider(objectMapper, WebClient.builder(), "test-key", url, "openai/gpt-oss-20b", 5000);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private ChatRequest req() {
        return new ChatRequest("sys", "친구 3명", "c", Map.of("type", "object"), 0, 512);
    }

    @Test
    void parsesContentAndSendsBearerAndSchema() throws Exception {
        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"players\\\":3}\"}}]}"));

        Optional<String> out = provider.complete(req());

        assertThat(out).contains("{\"players\":3}");
        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-key");
        assertThat(recorded.getBody().readUtf8()).contains("json_schema").contains("openai/gpt-oss-20b");
    }

    @Test
    void disabledWhenKeyBlank() {
        GroqChatProvider p = new GroqChatProvider(objectMapper, WebClient.builder(), "", url, "openai/gpt-oss-20b", 5000);
        assertThat(p.isEnabled()).isFalse();
    }

    @Test
    void completeReturnsEmptyImmediatelyWhenKeyBlank() {
        GroqChatProvider p = new GroqChatProvider(objectMapper, WebClient.builder(), "", url, "openai/gpt-oss-20b", 5000);
        assertThat(p.complete(req())).isEmpty();
        assertThat(server.getRequestCount()).isEqualTo(0);
    }

    @Test
    void fallbackReturnsEmpty() {
        assertThat(provider.fallback(req(), new RuntimeException("x"))).isEmpty();
    }

    @Test
    void malformedBodyReturnsEmpty() {
        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody("not json"));
        assertThat(provider.complete(req())).isEmpty();
    }

    @Test
    void koreanBodyPreservedEvenWhenBuilderEncoderCorrupts() throws Exception {
        // 기본 ObjectMapper 의 StringCleanerSerializer 가 붙은 오염 인코더를 빌더에 심어도
        // provider 가 깨끗한 인코더로 덮어써 아웃바운드 본문에 한글 원문이 보존되는지 검증한다
        ObjectMapper poisoned = new ObjectMapper();
        SimpleModule cleaner = new SimpleModule();
        cleaner.addSerializer(String.class, new StringCleanerSerializer());
        poisoned.registerModule(cleaner);
        WebClient.Builder poisonedBuilder = WebClient.builder()
                .codecs(c -> c.defaultCodecs().jackson2JsonEncoder(
                        new Jackson2JsonEncoder(poisoned, MediaType.APPLICATION_JSON)));
        GroqChatProvider p = new GroqChatProvider(objectMapper, poisonedBuilder, "test-key", url, "openai/gpt-oss-20b", 5000);

        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody(
                "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}"));
        p.complete(new ChatRequest("sys", "친구 3명이랑 호러 빼줘", "c", Map.of("type", "object"), 0, 512));

        String sentBody = server.takeRequest().getBody().readUtf8();
        assertThat(sentBody).contains("친구 3명이랑 호러 빼줘");
        assertThat(sentBody).doesNotContain("®").doesNotContain("™");
    }
}
