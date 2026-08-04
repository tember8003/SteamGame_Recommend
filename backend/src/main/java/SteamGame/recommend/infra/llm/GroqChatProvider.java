package SteamGame.recommend.infra.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
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

// Groq(openai 호환) chat completion 제공자임, response_format 의 json_schema 로 구조화 출력을 강제함
@Slf4j
@Component
@Order(1)
public class GroqChatProvider implements ChatCompletionProvider {

    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final long timeoutMs;

    public GroqChatProvider(ObjectMapper objectMapper, WebClient.Builder webClientBuilder,
                             @Value("${app.groq.api-key:}") String apiKey,
                             @Value("${app.groq.base-url:https://api.groq.com/openai/v1/chat/completions}") String baseUrl,
                             @Value("${app.groq.model:openai/gpt-oss-20b}") String model,
                             @Value("${app.groq.timeout-ms:5000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.webClient = LlmWebClientSupport.build(webClientBuilder);
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    // GROQ_API_KEY 미설정 배포를 기동 시점에 드러냄, 비활성 상태면 텍스트 작업이 Gemini 로만 폴백함
    @PostConstruct
    void warnIfDisabled() {
        if (!isEnabled()) {
            log.warn("GROQ_API_KEY 가 비어 Groq 제공자가 비활성임, 텍스트 작업이 Gemini 로만 폴백함");
        }
    }

    // 네트워크, HTTP 오류(onStatus 의 ResponseStatusException, timeout, 연결오류)는 잡지 않고 그대로 던져 Resilience4j 프록시가 실패로 기록하게 함
    // 파싱 실패만 이 메서드 안에서 흡수함, 서킷브레이커 개폐는 스프링 빈 간 호출(프록시 경유)에서만 걸리므로 단위테스트가 아니라 수기, 통합 검증 대상임
    @Override
    @CircuitBreaker(name = "groqApi", fallbackMethod = "fallback")
    public Optional<String> complete(ChatRequest request) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", request.system()),
                        Map.of("role", "user", "content", request.user())
                ),
                "temperature", request.temperature(),
                "max_completion_tokens", request.maxTokens(),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", request.schemaName(),
                                "strict", true,
                                "schema", request.jsonSchema()
                        )
                )
        );

        String raw = webClient.post()
                .uri(baseUrl)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        Mono.error(new ResponseStatusException(resp.statusCode(), "Groq API 오류")))
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .block();

        try {
            JsonNode choices = objectMapper.readTree(raw).path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return Optional.empty();
            }
            String content = choices.path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(content);
        } catch (Exception e) {
            log.error("Groq 응답 파싱 실패", e);
            return Optional.empty();
        }
    }

    public Optional<String> fallback(ChatRequest request, Throwable ex) {
        log.info("Groq API 실패 (schema={}): {}", request.schemaName(), ex.getMessage());
        return Optional.empty();
    }
}
