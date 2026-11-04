package SteamGame.recommend.domain.taste.content;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

// v2 문구 파일을 생성자에서 읽고 v1 축으로 검증한다, 실패하면 예외로 기동을 막는다
@Slf4j
@Component
public class TasteV2ContentLoader {

    static final String RESOURCE = "taste/taste-v2.json";

    private final TasteV2Content content;

    public TasteV2ContentLoader(ObjectMapper objectMapper, TasteContentLoader v1) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            this.content = read(objectMapper, in);
        } catch (IOException e) {
            throw new IllegalStateException("taste v2 content unreadable: " + RESOURCE, e);
        }
        TasteV2ContentValidator.validate(content, v1.content().axes());
        log.info("taste v2 content loaded: version={}, questions={}", content.version(), content.questions().size());
    }

    // 소수 점수를 정수로 잘라 받지 않도록 이 파일을 읽을 때만 소수 허용을 끈다
    static TasteV2Content read(ObjectMapper objectMapper, InputStream in) throws IOException {
        return objectMapper.readerFor(TasteV2Content.class)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .readValue(in);
    }

    public TasteV2Content content() {
        return content;
    }
}
