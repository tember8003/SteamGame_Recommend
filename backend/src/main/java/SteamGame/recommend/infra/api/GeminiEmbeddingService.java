package SteamGame.recommend.infra.api;

import SteamGame.recommend.infra.embedding.EmbeddingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 게임/질의 텍스트를 gemini-embedding 벡터로 변환한다.
 * 태그 추출용 {@link GeminiApiService} 와 동일한 WebClient·키·Resilience4j 패턴을 따른다.
 */
@Slf4j
@Service
public class GeminiEmbeddingService {
    // 요청 본문 직렬화 전용 ObjectMapper, 기본(@Primary) ObjectMapper 에는 StringCleanerSerializer 가 붙어
    // 비 Latin-1 문자(한글 등)를 물음표로 뭉개므로 임베딩 대상 텍스트를 원문 그대로 보내려면 깨끗한 인코더가 필요하다
    // 상태가 없어 스레드 안전함
    private static final ObjectMapper CLEAN_MAPPER = new ObjectMapper();

    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final EmbeddingProperties props;
    private final String gemini_api_key;

    public GeminiEmbeddingService(ObjectMapper objectMapper, WebClient.Builder webClientBuilder,
                                  EmbeddingProperties props,
                                  @Value("${spring.ai.google.api-key}") String gemini_api_key) {
        this.objectMapper = objectMapper;
        this.props = props;
        this.gemini_api_key = gemini_api_key;
        // 배치 임베딩 응답(최대 batchSize × dimension floats)이 WebClient 기본 256KB 버퍼를 초과 → 16MB 로 상향.
        // 아울러 요청 인코더를 깨끗한 ObjectMapper 로 덮어써 한글 텍스트가 파괴되지 않게 함(위 CLEAN_MAPPER 주석 참고)
        // 공유 빌더를 clone 해 다른 서비스(Steam/Gemini 태그)에는 영향을 주지 않음
        this.webClient = webClientBuilder.clone()
                .codecs(c -> {
                    c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024);
                    c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(CLEAN_MAPPER));
                })
                .build();
    }

    // 단건 임베딩 (질의 텍스트용). 실패 시 null 반환 -> 호출부가 구경로로 degrade
    @Retry(name = "geminiApi", fallbackMethod = "fallbackEmbed")
    @CircuitBreaker(name = "geminiApi", fallbackMethod = "fallbackEmbed")
    public float[] embed(String text) {
        Map<String, Object> body = Map.of(
                "model", "models/" + props.getModel(),
                "content", Map.of("parts", List.of(Map.of("text", text))),
                "taskType", props.getTaskType(),
                "outputDimensionality", props.getDimension()
        );

        String raw = post(props.getModel() + ":embedContent", body, Duration.ofSeconds(5));

        try {
            JsonNode values = objectMapper.readTree(raw).path("embedding").path("values");
            if (!values.isArray() || values.isEmpty()) {
                log.warn("Embedding 응답에 values 없음");
                return null;
            }
            return toFloatArray(values);
        } catch (Exception e) {
            log.error("Embedding 파싱 실패", e);
            return null;
        }
    }

    // 배치 임베딩 (백필용). 실패 시 빈 리스트 반환
    @Retry(name = "geminiApi", fallbackMethod = "fallbackEmbedBatch")
    @CircuitBreaker(name = "geminiApi", fallbackMethod = "fallbackEmbedBatch")
    public List<float[]> embedBatch(List<String> texts) {
        List<Map<String, Object>> requests = new ArrayList<>();
        for (String text : texts) {
            requests.add(Map.of(
                    "model", "models/" + props.getModel(),
                    "content", Map.of("parts", List.of(Map.of("text", text))),
                    "taskType", props.getTaskType(),
                    "outputDimensionality", props.getDimension()
            ));
        }
        Map<String, Object> body = Map.of("requests", requests);

        // 배치는 응답이 커서 단건보다 오래 걸림 → 배치 전용 타임아웃 사용
        String raw = post(props.getModel() + ":batchEmbedContents", body, Duration.ofMillis(props.getBatchTimeoutMs()));

        try {
            JsonNode embeddings = objectMapper.readTree(raw).path("embeddings");
            List<float[]> result = new ArrayList<>();
            for (JsonNode e : embeddings) {
                result.add(toFloatArray(e.path("values")));
            }
            return result;
        } catch (Exception e) {
            log.error("Batch embedding 파싱 실패", e);
            return Collections.emptyList();
        }
    }

    private String post(String pathAndMethod, Map<String, Object> body, Duration timeout) {
        return webClient
                .post()
                .uri(props.getBaseUrl() + "/" + pathAndMethod + "?key=" + gemini_api_key)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        Mono.error(new ResponseStatusException(resp.statusCode(), "Gemini Embedding API 오류"))
                )
                .bodyToMono(String.class)
                .timeout(timeout)
                .block();
    }

    private float[] toFloatArray(JsonNode values) {
        float[] vec = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vec[i] = (float) values.get(i).asDouble();
        }
        return vec;
    }

    public float[] fallbackEmbed(String text, Throwable ex) {
        log.info("Embedding API 실패 (len={}): {}", text == null ? 0 : text.length(), ex.getMessage());
        return null;
    }

    public List<float[]> fallbackEmbedBatch(List<String> texts, Throwable ex) {
        log.info("Batch embedding API 실패 (size={}): {}", texts == null ? 0 : texts.size(), ex.getMessage());
        return Collections.emptyList();
    }
}
