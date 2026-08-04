package SteamGame.recommend.infra.llm;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// Groq, Gemini 등 활성 제공자를 @Order 순서대로 시도해 첫 비어있지 않은 응답을 반환함, 다 실패하면 empty 를 줌
@Component
public class ChatCompletionRouter {

    private final List<ChatCompletionProvider> providers;

    public ChatCompletionRouter(List<ChatCompletionProvider> providers) {
        this.providers = providers;
    }

    public Optional<String> complete(ChatRequest request) {
        for (ChatCompletionProvider provider : providers) {
            if (!provider.isEnabled()) {
                continue;
            }
            Optional<String> result = provider.complete(request);
            if (result.isPresent() && !result.get().isBlank()) {
                return result;
            }
        }
        return Optional.empty();
    }
}
