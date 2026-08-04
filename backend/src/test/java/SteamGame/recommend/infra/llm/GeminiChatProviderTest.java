package SteamGame.recommend.infra.llm;

import SteamGame.recommend.config.StringCleanerSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiChatProviderTest {

    MockWebServer server;
    String url;
    ObjectMapper objectMapper;
    GeminiChatProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        objectMapper = new ObjectMapper();
        url = server.url("").toString().replaceAll("/$", "");
        provider = new GeminiChatProvider(objectMapper, WebClient.builder(), "test-key", url);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private ChatRequest req() {
        return new ChatRequest("sys", "user", "t", Map.of(), 0, 512);
    }

    @Test
    void parsesGeminiTextAndSendsKey() throws Exception {
        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"[\\\"RPG\\\"]\"}]}}]}"));

        Optional<String> out = provider.complete(new ChatRequest("sys", "user", "t", Map.of(), 0, 512));

        assertThat(out).contains("[\"RPG\"]");
        assertThat(server.takeRequest().getPath()).contains("key=");
    }

    @Test
    void disabledWhenKeyBlank() {
        GeminiChatProvider p = new GeminiChatProvider(objectMapper, WebClient.builder(), "", url);
        assertThat(p.isEnabled()).isFalse();
    }

    @Test
    void completeReturnsEmptyImmediatelyWhenKeyBlank() {
        GeminiChatProvider p = new GeminiChatProvider(objectMapper, WebClient.builder(), "", url);
        assertThat(p.complete(req())).isEmpty();
        assertThat(server.getRequestCount()).isEqualTo(0);
    }

    @Test
    void fallbackReturnsEmpty() {
        // 프록시가 잡은 오류를 흡수하는 폴백 메서드는 항상 empty 를 줌
        assertThat(provider.fallback(req(), new RuntimeException("boom"))).isEmpty();
    }

    @Test
    void malformedBodyReturnsEmpty() {
        // 파싱 실패(구조 안 맞는 응답)는 complete 안에서 흡수해 empty 를 반환함
        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody("{\"candidates\":[]}"));
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
        GeminiChatProvider p = new GeminiChatProvider(objectMapper, poisonedBuilder, "test-key", url);

        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"[]\"}]}}]}"));
        p.complete(new ChatRequest("sys", "친구 3명이랑 호러 빼줘", "t", Map.of(), 0, 512));

        String sentBody = server.takeRequest().getBody().readUtf8();
        assertThat(sentBody).contains("친구 3명이랑 호러 빼줘");
        assertThat(sentBody).doesNotContain("®").doesNotContain("™");
    }
}
