package SteamGame.recommend.infra.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

// LLM 아웃바운드 요청 본문 직렬화용 WebClient 조립 지점임
// 기본(@Primary) ObjectMapper 에는 StringCleanerSerializer 가 붙어 비 Latin-1 문자(한글 등)를 물음표로 뭉개므로
// LLM 요청에는 깨끗한 ObjectMapper 를 인코더로 지정해 원문을 그대로 보낸다
final class LlmWebClientSupport {

    // 상태가 없어 스레드 안전함, 요청 본문 Map 직렬화에만 쓴다
    private static final ObjectMapper CLEAN_MAPPER = new ObjectMapper();

    private LlmWebClientSupport() {
    }

    // 주입받은 빌더를 복제해 Jackson 인코더만 깨끗한 것으로 덮어써 build 한다, 원본 빌더는 건드리지 않는다
    static WebClient build(WebClient.Builder webClientBuilder) {
        return webClientBuilder.clone()
                .codecs(config -> config.defaultCodecs().jackson2JsonEncoder(
                        new Jackson2JsonEncoder(CLEAN_MAPPER)))
                .build();
    }
}
