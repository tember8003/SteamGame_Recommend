package SteamGame.recommend.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        // String 시리얼라이저(StringCleanerSerializer)는 붙이지 않는다
        // H2 시절 이중 인코딩 데이터를 출력에서 땜빵하던 유물인데, 현재 postgres 데이터는 정상 UTF-8 이라
        // 붙이면 비 Latin-1 문자(한글, ™, 곡선따옴표 등)를 오히려 ® 로 파괴함(응답, LLM 요청 양쪽) — 재도입 금지
        // 혹시 남은 레거시 모지바케 행이 있으면 DB 데이터를 고치지 여기서 직렬화로 다시 땜빵하지 않는다
        return mapper;
    }
}
