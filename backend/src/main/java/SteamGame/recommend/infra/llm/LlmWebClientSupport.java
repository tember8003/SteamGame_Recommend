package SteamGame.recommend.infra.llm;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

// LLM 아웃바운드 요청 본문 직렬화용 WebClient 조립 지점임
// 예전 기본 ObjectMapper 에 붙어 있던 StringCleanerSerializer 가 한글을 뭉갠 적이 있어서
// 기본 매퍼 설정이 다시 바뀌어도 영향받지 않게 LLM 요청에는 따로 만든 깨끗한 매퍼를 인코더로 지정한다
final class LlmWebClientSupport {

    // 상태가 없어 스레드 안전함, 요청 본문 Map 직렬화에만 쓴다
    private static final JsonMapper CLEAN_MAPPER = JsonMapper.builderWithJackson2Defaults().build();

    private LlmWebClientSupport() {
    }

    // 주입받은 빌더를 복제해 Jackson 인코더만 깨끗한 것으로 덮어써 build 한다, 원본 빌더는 건드리지 않는다
    static WebClient build(WebClient.Builder webClientBuilder) {
        return webClientBuilder.clone()
                .codecs(config -> config.defaultCodecs().jacksonJsonEncoder(
                        new JacksonJsonEncoder(CLEAN_MAPPER)))
                .build();
    }
}
