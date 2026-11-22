package SteamGame.recommend.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;

@Configuration
public class JacksonConfig {

    // Jackson 3 의 매퍼는 만든 뒤 설정을 못 바꾸므로, 직접 매퍼를 만들지 않고 Boot 가 만드는 JsonMapper 에 설정만 얹는다
    // 그래서 MVC 응답과 주입받는 ObjectMapper 가 같은 매퍼다, 날짜 모듈은 Jackson 3 에 기본으로 들어 있다
    //
    // String 시리얼라이저(StringCleanerSerializer)는 붙이지 않는다
    // H2 시절 이중 인코딩 데이터를 출력에서 땜빵하던 유물인데, 현재 postgres 데이터는 정상 UTF-8 이라
    // 붙이면 비 Latin-1 문자(한글, ™, 곡선따옴표 등)를 오히려 ® 로 파괴함(응답, LLM 요청 양쪽) — 재도입 금지
    // 혹시 남은 레거시 모지바케 행이 있으면 DB 데이터를 고치지 여기서 직렬화로 다시 땜빵하지 않는다
    @Bean
    public JsonMapperBuilderCustomizer jsonMapperCustomizer() {
        return builder -> builder
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
}
