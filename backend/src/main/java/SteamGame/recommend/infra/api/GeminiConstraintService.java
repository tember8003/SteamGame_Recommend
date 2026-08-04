package SteamGame.recommend.infra.api;

import SteamGame.recommend.domain.recommendation.dto.ParsedConstraints;
import SteamGame.recommend.domain.recommendation.dto.SessionLength;
import SteamGame.recommend.infra.llm.ChatCompletionRouter;
import SteamGame.recommend.infra.llm.ChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 자연어 한 줄을 채팅 라우터로 파싱해 구조화 제약(ParsedConstraints)으로 변환한다
 * 실제 제공자 선택, 재시도, 폴백은 ChatCompletionRouter 아래쪽(GroqChatProvider, GeminiChatProvider)에서 처리함
 */
@Slf4j
@Service
public class GeminiConstraintService {

    // getGeminiTags 와 동일한 태그 리스트, excludeTags 는 이 목록 안에서만 고르도록 프롬프트에 그대로 노출함
    private static final List<String> TAG_LIST = List.of(
            "2D", "3D", "RPG", "Action", "Roguelike", "Adventure", "Casual", "Indie", "Strategy", "Simulation",
            "Multiplayer", "Singleplayer", "Co-op", "Early Access", "Open World", "Story Rich", "Puzzle", "Platformer",
            "Shooter", "FPS", "Third Person", "VR", "Fantasy", "Sci-fi", "Horror", "Survival", "Anime", "Visual Novel",
            "Pixel Graphics", "Turn-Based", "Card Game", "Sandbox", "Building", "Crafting", "Mystery", "Comedy", "Dark",
            "Gore", "Violent", "Cute", "Psychological Horror", "Detective", "Zombies", "Online Co-Op", "Hack and Slash",
            "Fighting", "Beat 'em up", "Bullet Hell", "Side Scroller", "Roguelike", "Roguelite", "Action RPG",
            "Action-Adventure", "MMO", "JRPG", "Dungeon Crawler", "Match 3", "Sports", "Racing", "First-Person",
            "Third-Person Shooter", "Rhythm", "Music", "Management", "Clicker", "Tactical", "Stealth", "Exploration"
    );

    private static final String TAG_LIST_TEXT = "[" + TAG_LIST.stream()
            .map(t -> "\"" + t + "\"")
            .collect(Collectors.joining(", ")) + "]";

    // Groq strict 모드 요구사항을 따름, 모든 프로퍼티는 required 에 포함하고 additionalProperties 는 false 로 고정함
    // nullable 값은 type 을 union([타입, "null"]) 으로 표현하고 nullable enum 은 enum 배열 안에 null 을 포함시킴(List.of 는 null 불허라 Arrays.asList 사용)
    private static final Map<String, Object> CONSTRAINT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "players", Map.of("type", List.of("integer", "null")),
                    "budgetUsd", Map.of("type", List.of("integer", "null")),
                    "sessionLength", Map.of("type", List.of("string", "null"),
                            "enum", Arrays.asList("SHORT", "MEDIUM", "LONG", null)),
                    "needsCoop", Map.of("type", List.of("boolean", "null")),
                    "moodText", Map.of("type", List.of("string", "null")),
                    "excludeTags", Map.of("type", "array", "items", Map.of("type", "string"))
            ),
            "required", List.of("players", "budgetUsd", "sessionLength", "needsCoop", "moodText", "excludeTags"),
            "additionalProperties", false
    );

    private final ObjectMapper objectMapper;
    private final ChatCompletionRouter router;

    public GeminiConstraintService(ObjectMapper objectMapper, ChatCompletionRouter router) {
        this.objectMapper = objectMapper;
        this.router = router;
    }

    // 자연어 한 줄을 구조화 제약으로 바꾸며 빈 입력이거나 파싱 중 하나라도 실패하면 null 을 반환한다
    public ParsedConstraints parseConstraints(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        ChatRequest req = new ChatRequest(
                "You extract structured constraints as JSON.",
                buildPrompt(rawText),
                "constraints",
                CONSTRAINT_SCHEMA,
                0,
                1024
        );

        Optional<String> out = router.complete(req);
        if (out.isEmpty()) {
            return null;
        }

        try {
            Matcher m = Pattern.compile("\\{.*\\}", Pattern.DOTALL).matcher(out.get());

            if (m.find()) {
                JsonNode constraints = objectMapper.readTree(m.group());
                return toParsedConstraints(constraints);
            }
        } catch (Exception e) {
            log.error("Gemini Constraint parsing failed", e);
        }

        return null;
    }

    private String buildPrompt(String input) {
        return """
        Analyze the following sentence and extract these fields as a single JSON object, output nothing else (no markdown fence, no explanation).

        Fields:
        - players: integer, how many people are playing together (null if not mentioned)
        - budgetUsd: integer, budget in US dollars (null if not mentioned)
        - sessionLength: one of "SHORT", "MEDIUM", "LONG" (null if not mentioned)
        - needsCoop: boolean, whether cooperative play is required (null if not mentioned)
        - moodText: string, a short phrase describing the desired mood or atmosphere (null if not mentioned)
        - excludeTags: array of strings picked only from the tag list below, tags to exclude (empty array if none)

        Tag list:
        %s

        Example:
        Input sentence: 친구 3명이랑 20달러 이하 협동, 호러 빼줘
        Output: {"players":3,"budgetUsd":20,"sessionLength":null,"needsCoop":true,"moodText":null,"excludeTags":["Horror"]}

        Input sentence: %s
        Output:
        """.formatted(TAG_LIST_TEXT, input);
    }

    private ParsedConstraints toParsedConstraints(JsonNode node) {
        return new ParsedConstraints(
                asIntOrNull(node, "players"),
                asIntOrNull(node, "budgetUsd"),
                asSessionLengthOrNull(node, "sessionLength"),
                asBooleanOrNull(node, "needsCoop"),
                asTextOrNull(node, "moodText"),
                asExcludeTagsOrNull(node, "excludeTags")
        );
    }

    // 타입이 어긋난 값(예: 문자열 "twenty")은 asInt() 가 0 으로 조용히 뭉개므로 숫자 노드일 때만 변환한다
    private Integer asIntOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull() || !v.isNumber()) ? null : v.asInt();
    }

    // 타입이 어긋난 값은 asBoolean() 이 false 로 조용히 뭉개므로 불리언 노드일 때만 변환한다
    private Boolean asBooleanOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull() || !v.isBoolean()) ? null : v.asBoolean();
    }

    private String asTextOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull()) ? null : v.asText();
    }

    // enum 에 없는 문자열은 잘못된 응답으로 보고 null 로 흡수함
    private SessionLength asSessionLengthOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        try {
            return SessionLength.valueOf(v.asText().trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    // 태그 리스트에 없는 값은 환각으로 보고 걸러내며, 필드 자체가 없으면 null 로 둔다
    private List<String> asExcludeTagsOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isArray()) {
            return null;
        }
        List<String> tags = new ArrayList<>();
        for (JsonNode item : v) {
            if (item.isTextual() && TAG_LIST.contains(item.asText())) {
                tags.add(item.asText());
            }
        }
        return tags;
    }
}
