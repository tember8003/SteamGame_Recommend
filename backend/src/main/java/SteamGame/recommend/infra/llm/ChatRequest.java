package SteamGame.recommend.infra.llm;

import java.util.Map;

// 채팅 완성 요청 하나를 표현하는 불변 값 객체임, jsonSchema 는 Groq structured output 스키마 그대로 전달함
public record ChatRequest(String system, String user, String schemaName, Map<String, Object> jsonSchema,
                           double temperature, int maxTokens) {
}
