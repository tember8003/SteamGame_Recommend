package SteamGame.recommend.infra.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatCompletionRouterTest {

    ChatCompletionProvider groq = mock(ChatCompletionProvider.class);
    ChatCompletionProvider gemini = mock(ChatCompletionProvider.class);

    private ChatRequest req() {
        return new ChatRequest("sys", "user", "t", Map.of(), 0, 512);
    }

    @Test
    void groqSuccessSkipsGemini() {
        when(groq.isEnabled()).thenReturn(true);
        when(groq.complete(any())).thenReturn(Optional.of("{\"ok\":1}"));
        ChatCompletionRouter router = new ChatCompletionRouter(List.of(groq, gemini));
        assertThat(router.complete(req())).contains("{\"ok\":1}");
        verifyNoInteractions(gemini);
    }

    @Test
    void groqEmptyFallsToGemini() {
        when(groq.isEnabled()).thenReturn(true);
        when(groq.complete(any())).thenReturn(Optional.empty());
        when(gemini.isEnabled()).thenReturn(true);
        when(gemini.complete(any())).thenReturn(Optional.of("g"));
        assertThat(new ChatCompletionRouter(List.of(groq, gemini)).complete(req())).contains("g");
    }

    @Test
    void allFailReturnsEmpty() {
        when(groq.isEnabled()).thenReturn(true);
        when(groq.complete(any())).thenReturn(Optional.empty());
        when(gemini.isEnabled()).thenReturn(true);
        when(gemini.complete(any())).thenReturn(Optional.empty());
        assertThat(new ChatCompletionRouter(List.of(groq, gemini)).complete(req())).isEmpty();
    }

    @Test
    void disabledProviderSkipped() {
        when(groq.isEnabled()).thenReturn(false);
        when(gemini.isEnabled()).thenReturn(true);
        when(gemini.complete(any())).thenReturn(Optional.of("g"));
        assertThat(new ChatCompletionRouter(List.of(groq, gemini)).complete(req())).contains("g");
        verify(groq, never()).complete(any());
    }
}
