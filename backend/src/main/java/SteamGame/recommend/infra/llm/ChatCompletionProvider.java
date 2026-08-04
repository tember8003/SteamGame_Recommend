package SteamGame.recommend.infra.llm;

import java.util.Optional;

// 텍스트 LLM 채팅 완성 제공자 추상화임, 구현체를 여러 개 두고 @Order 로 우선순위를 정함
public interface ChatCompletionProvider {

    boolean isEnabled();

    Optional<String> complete(ChatRequest request);
}
