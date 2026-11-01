package SteamGame.recommend.domain.library.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

// 문구 파일을 생성자에서 읽고 검증한다. 실패하면 예외로 기동을 막는다
@Slf4j
@Component
public class LibraryContentLoader {

    static final String RESOURCE = "library/library-v1.json";

    private final LibraryContent content;

    public LibraryContentLoader(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            this.content = objectMapper.readValue(in, LibraryContent.class);
        } catch (IOException e) {
            throw new IllegalStateException("library content unreadable: " + RESOURCE, e);
        }
        LibraryContentValidator.validate(content);
        log.info("library content loaded: version={}, tagGroups={}, phraseCodes={}",
                content.version(), content.tagGroups().size(), content.phrases().size());
    }

    public LibraryContent content() {
        return content;
    }
}
