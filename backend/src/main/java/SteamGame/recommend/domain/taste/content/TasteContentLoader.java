package SteamGame.recommend.domain.taste.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

// 문구 파일을 생성자에서 읽고 검증한다. 실패하면 예외로 기동을 막는다
@Slf4j
@Component
public class TasteContentLoader {

    static final String RESOURCE = "taste/taste-v1.json";

    private final TasteContent content;

    public TasteContentLoader(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            this.content = objectMapper.readValue(in, TasteContent.class);
        } catch (IOException e) {
            throw new IllegalStateException("taste content unreadable: " + RESOURCE, e);
        }
        TasteContentValidator.validate(content);
        log.info("taste content loaded: version={}, questions={}, types={}",
                content.version(), content.questions().size(), content.types().size());
    }

    public TasteContent content() {
        return content;
    }
}
