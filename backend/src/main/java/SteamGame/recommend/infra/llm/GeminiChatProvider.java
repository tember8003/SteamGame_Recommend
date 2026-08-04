package SteamGame.recommend.infra.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Gemini chat completion 제공자임, Groq 실패 시 폴백으로 쓰임, jsonSchema 는 무시하고 프롬프트로 출력 형식을 유도함
@Slf4j
@Component
@Order(2)
public class GeminiChatProvider implements ChatCompletionProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final String apiKey;
    private final String chatUrl;

    public GeminiChatProvider(ObjectMapper objectMapper, WebClient.Builder webClientBuilder,
                               @Value("${spring.ai.google.api-key:}") String apiKey,
                               @Value("${app.gemini.chat-url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent}") String chatUrl) {
        this.objectMapper = objectMapper;
        this.webClient = LlmWebClientSupport.build(webClientBuilder);
        this.apiKey = apiKey;
        this.chatUrl = chatUrl;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    // 네트워크, HTTP 오류(onStatus 의 ResponseStatusException, timeout, 연결오류)는 잡지 않고 그대로 던져 Resilience4j 프록시가 실패로 기록하게 함
    // 파싱 실패만 이 메서드 안에서 흡수함, 서킷브레이커 개폐는 스프링 빈 간 호출(프록시 경유)에서만 걸리므로 단위테스트가 아니라 수기,통합 검증 대상임
    @Override
    @CircuitBreaker(name = "geminiApi", fallbackMethod = "fallback")
    public Optional<String> complete(ChatRequest request) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        String prompt = request.system() + "\n" + request.user();
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", request.temperature(),
                        "maxOutputTokens", request.maxTokens()
                )
        );

        String raw = webClient.post()
                .uri(chatUrl + "?key=" + apiKey)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        Mono.error(new ResponseStatusException(resp.statusCode(), "Gemini API 오류")))
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

        try {
            JsonNode candidates = objectMapper.readTree(raw).path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return Optional.empty();
            }
            String text = candidates.path(0).path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(text);
        } catch (Exception e) {
            log.error("Gemini 응답 파싱 실패", e);
            return Optional.empty();
        }
    }

    public Optional<String> fallback(ChatRequest request, Throwable ex) {
        log.info("Gemini API 실패 (schema={}): {}", request.schemaName(), ex.getMessage());
        return Optional.empty();
    }
}
