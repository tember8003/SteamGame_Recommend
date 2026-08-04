package SteamGame.recommend.infra.api;

import SteamGame.recommend.infra.llm.ChatCompletionRouter;
import SteamGame.recommend.infra.llm.ChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiApiServiceTagsTest {

    ChatCompletionRouter router;
    GeminiApiService service;

    @BeforeEach
    void setUp() {
        router = mock(ChatCompletionRouter.class);
        service = new GeminiApiService(new ObjectMapper(), router);
    }

    @Test
    void parsesTagsFromObjectOutput() {
        when(router.complete(any())).thenReturn(Optional.of("{\"tags\":[\"RPG\",\"Co-op\"]}"));

        List<String> tags = service.getGeminiTags("친구랑 즐길 협동 RPG");

        assertThat(tags).containsExactly("RPG", "Co-op");

        // 라우터로 전송된 스키마가 스왑, 오타 없이 strict 요구사항을 지키는지 구조로 검증함, 상수 자체는 참조하지 않음
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(router).complete(captor.capture());
        ChatRequest sent = captor.getValue();

        assertThat(sent.schemaName()).isEqualTo("tags");
        assertThat(sent.jsonSchema().get("additionalProperties")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Object> required = (List<Object>) sent.jsonSchema().get("required");
        assertThat(required).containsExactly("tags");

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) sent.jsonSchema().get("properties");
        assertThat(properties.keySet()).containsExactlyInAnyOrder("tags");
    }

    @Test
    void filtersOutNonStringElements() {
        when(router.complete(any())).thenReturn(Optional.of("{\"tags\":[\"RPG\",123,{\"x\":1}]}"));

        List<String> tags = service.getGeminiTags("아무 문장");

        assertThat(tags).containsExactly("RPG");
    }

    @Test
    void limitsToFiveTags() {
        when(router.complete(any())).thenReturn(Optional.of(
                "{\"tags\":[\"A\",\"B\",\"C\",\"D\",\"E\",\"F\"]}"
        ));

        List<String> tags = service.getGeminiTags("아무 문장");

        assertThat(tags).hasSize(5);
        assertThat(tags).containsExactly("A", "B", "C", "D", "E");
    }

    @Test
    void emptyRouterResultReturnsEmptyList() {
        when(router.complete(any())).thenReturn(Optional.empty());

        assertThat(service.getGeminiTags("아무 문장")).isEmpty();
    }

    @Test
    void bareArrayOutputIsAlsoAccepted() {
        when(router.complete(any())).thenReturn(Optional.of("[\"RPG\"]"));

        List<String> tags = service.getGeminiTags("아무 문장");

        assertThat(tags).containsExactly("RPG");
    }
}
