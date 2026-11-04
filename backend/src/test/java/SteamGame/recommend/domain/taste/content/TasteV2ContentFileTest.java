package SteamGame.recommend.domain.taste.content;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 실제 taste-v2.json을 로더로 읽어 확인한다
class TasteV2ContentFileTest {

    private static final TasteContentLoader V1 = new TasteContentLoader(new ObjectMapper());

    private static TasteV2Content load() {
        return new TasteV2ContentLoader(new ObjectMapper(), V1).content();
    }

    @Test
    void loadsAndPassesValidation() {
        TasteV2Content c = load();
        assertThat(c.version()).isEqualTo("v2");
        assertThat(c.questions()).hasSize(16);
    }

    // 설계 문서 5절 1번 문항의 축과 점수 배치가 그대로 들어갔는지 본다, 문구는 사용자가 파일에서 바꾸므로 비교하지 않는다
    @Test
    void firstQuestionScoresMatchDesign() {
        TasteV2Question q1 = load().questions().get(0);
        assertThat(q1.axis()).isEqualTo("EG");
        assertThat(q1.options()).extracting(o -> o.scores().get("E")).containsExactly(3, 1, 2, 0);
        assertThat(q1.options()).extracting(o -> o.scores().get("G")).containsExactly(0, 2, 1, 3);
    }

    // scores에 소수를 쓰면 기동 때 거부되는지 본다, Jackson 기본값은 소수를 정수로 잘라 받아들이므로 read에서 그 기본값을 꺼야 통과한다
    @Test
    void read_rejectsFractionalScore() throws IOException {
        String raw = rawContent();
        String mutated = raw.replaceFirst("\"E\": 1,", "\"E\": 1.5,");
        assertThat(mutated).isNotEqualTo(raw);
        assertThatThrownBy(() -> TasteV2ContentLoader.read(new ObjectMapper(),
                new ByteArrayInputStream(mutated.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(JsonMappingException.class);
    }

    // 소수를 허용하지 않아도 정수만 쓴 실제 파일은 그대로 읽히는지 본다
    @Test
    void read_acceptsRealFileWithIntegerScores() throws IOException {
        String raw = rawContent();
        TasteV2Content c = TasteV2ContentLoader.read(new ObjectMapper(),
                new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        assertThat(c.version()).isEqualTo("v2");
        assertThat(c.questions()).hasSize(16);
    }

    private static String rawContent() throws IOException {
        try (InputStream in = new ClassPathResource(TasteV2ContentLoader.RESOURCE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // 문구 완성 관문이다, 사용자와 GPT가 모든 TODO를 채우기 전까지 일부러 실패한다
    // 이 테스트가 실패하는 동안에는 PR을 머지하지 않는다
    @Test
    void noTodoTextLeft() {
        TasteV2Content c = load();
        List<String> todo = new ArrayList<>();
        for (TasteV2Question q : c.questions()) {
            collect("question " + q.id(), q.text(), todo);
            for (TasteV2Option o : q.options()) {
                collect("question " + q.id() + " option " + o.key(), o.text(), todo);
            }
        }
        c.axisSummaries().forEach((axis, byKey) ->
                byKey.forEach((key, t) -> collect("axisSummaries " + axis + " " + key, t, todo)));
        assertThat(todo).as("TODO가 남은 문구").isEmpty();
    }

    private static void collect(String label, LocalizedText t, List<String> out) {
        if (t.ko().contains("TODO")) {
            out.add(label + ".ko");
        }
        if (t.en().contains("TODO")) {
            out.add(label + ".en");
        }
    }
}
