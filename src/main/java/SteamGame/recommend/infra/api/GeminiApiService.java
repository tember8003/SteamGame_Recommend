package SteamGame.recommend.infra.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GeminiApiService {
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private final String gemini_api_key;
    private final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    public GeminiApiService(ObjectMapper objectMapper,WebClient.Builder webClientBuilder, @Value("${spring.ai.google.api-key}") String gemini_api_key){
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
        this.gemini_api_key = gemini_api_key;
    }

    @Retry(name="geminiApi", fallbackMethod = "fallbackGEMINI")
    @CircuitBreaker(name="geminiApi",fallbackMethod = "fallbackGEMINI")
    public List<String> getGeminiTags(String input) {

        String prompt = buildPrompt(input);

        Map<String,Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature",1, "maxOutputTokens",8192)
        );

        String raw = webClientBuilder.build()
                .post()
                .uri(GEMINI_URL + "?key=" + gemini_api_key)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        Mono.error(new ResponseStatusException(resp.statusCode(), "Gemini API 오류"))
                )
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .block();

        try {
            JsonNode root = objectMapper.readTree(raw);
            String text = root.path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();

            Matcher m = Pattern.compile("\\[.*?\\]", Pattern.DOTALL).matcher(text);

            if (m.find()) {
                String jsonArray = m.group();
                List<String> tags = objectMapper.readValue(
                        jsonArray, new TypeReference<List<String>>() {}
                );
                return tags.stream().limit(5).toList();
            }
        } catch (Exception e) {
            log.error("Gemini 태그 파싱 실패", e);
        }

        return Collections.emptyList();
    }

    private String buildPrompt(String input) {
        return """
        다음 문장을 보고 아래 태그 중 관련된 태그를 최대 5개 추출해 JSON 배열 형태로 출력해.
        
        태그 목록:
        ["2D", "3D", "RPG", "액션", "무슬", "어드벤처", "캐주얼", "인디", "전략", "시뮬레이션", "멀티플레이어", "싱글 플레이어", "협동", "앞서 해보기", "오픈 월드", "풍부한 스토리", "퍼즐", "플랫폼", "슈팅", "FPS", "3인칭", "VR", "판타지", "SF", "공포", "생존", "애니메이션", "비주얼 노벨", "픽셀 그래픽", "턴제", "카드 게임", "샌드박스", "건설", "크래프팅", "미스터리", "코미디", "어두운", "고어", "폭력", "귀여운", "심리적 공포", "수사", "좀비", "온라인 협동", "핵 앤 슬래시", "격투", "비뎀업", "탄막 슈팅", "횡스크롤", "로그라이크", "로그라이트", "액션 RPG", "액션 어드벤처", "MMO", "JRPG", "던전 크롤러", "매치 3", "스포츠", "레이싱", "1인칭", "3인칭 슈팅", "리듬", "음악", "경영", "클리커", "전술", "잠입", "탐험"]
        
        예시 1:
        입력 문장: 롤 같은 게임
        출력: ["MOBA", "멀티플레이어", "실시간"]
        
        예시 2:
        입력 문장: 친구와 같이 즐길 수 있는 로그라이크 게임 추천해줘
        출력: ["로그라이크", "멀티플레이어", "협동"]
        
        예시 3:
        입력 문장: 혼자 조용히 몰입해서 할 수 있는 감성적인 스토리 게임
        출력: ["싱글 플레이어", "풍부한 스토리", "어드벤처"]
        예시 4:
        입력 문장: 스팀덱으로 하기 좋은 픽셀 그래픽 로그라이크
        출력: ["픽셀 그래픽", "로그라이크", "싱글 플레이어"]
        
        예시 5:
        입력 문장: 친구랑 밤새도록 할 수 있는 도전적인 게임
        출력: ["협동", "로그라이크", "고난이도"]
       
        이런 식으로 스팀 태그 목록에서 뽑아내서 출력해줘.
        
        입력 문장: %s
        출력:
        """.formatted(input);
    }

    public List<String> fallbackGEMINI(String input,Throwable ex){
        log.info("GEMINI API 동기화 실패 (input={}): {}", input, ex.getMessage());

        return Collections.emptyList();
    }
}
