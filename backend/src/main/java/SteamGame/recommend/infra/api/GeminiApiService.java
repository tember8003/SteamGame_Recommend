package SteamGame.recommend.infra.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import SteamGame.recommend.infra.llm.ChatCompletionRouter;
import SteamGame.recommend.infra.llm.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GeminiApiService {

    // Groq strict 모드 요구사항을 따름, tags 는 required 에 포함하고 additionalProperties 는 false 로 고정함
    private static final Map<String, Object> TAGS_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "tags", Map.of("type", "array", "items", Map.of("type", "string"))
            ),
            "required", List.of("tags"),
            "additionalProperties", false
    );

    private final ObjectMapper objectMapper;
    private final ChatCompletionRouter router;

    public GeminiApiService(ObjectMapper objectMapper, ChatCompletionRouter router) {
        this.objectMapper = objectMapper;
        this.router = router;
    }

    public List<String> getGeminiTags(String input) {

        ChatRequest req = new ChatRequest(
                "You extract Steam tags as JSON.",
                buildPrompt(input),
                "tags",
                TAGS_SCHEMA,
                1,
                1024
        );

        Optional<String> out = router.complete(req);
        if (out.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = parseTagsRoot(out.get());
            JsonNode tagsNode = root.isArray() ? root : root.path("tags");

            List<String> tags = new ArrayList<>();
            if (tagsNode.isArray()) {
                for (JsonNode item : tagsNode) {
                    if (item.isTextual()) {
                        tags.add(item.asText());
                    }
                }
            }
            return tags.stream().limit(5).toList();
        } catch (Exception e) {
            log.error("Gemini Tag parsing failed", e);
        }

        return Collections.emptyList();
    }

    // 마크다운 펜스나 설명 문구로 감싸진 Gemini 폴백 응답도 견디도록 함, 곧바로 파싱이 되지 않거나 배열도 tags 를 가진 객체도 아니면
    // 본문에서 첫 JSON 객체나 배열을 정규식으로 추출해 재파싱함
    private JsonNode parseTagsRoot(String content) throws Exception {
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root.isArray() || root.has("tags")) {
                return root;
            }
        } catch (Exception ignored) {
            // 아래 정규식 재추출로 넘어감
        }

        Matcher m = Pattern.compile("\\{.*\\}|\\[.*\\]", Pattern.DOTALL).matcher(content);
        if (m.find()) {
            return objectMapper.readTree(m.group());
        }
        throw new IllegalArgumentException("Gemini tags 응답에서 JSON 을 찾지 못함");
    }

    private String buildPrompt(String input) {
        return """
        Analyze the following sentence and extract up to 5 related tags from the tag list below. Output a single JSON object of the form {"tags": ["Tag1", "Tag2"]}, output nothing else (no markdown fence).

        Tag list:
        ["2D", "3D", "RPG", "Action", "Roguelike", "Adventure", "Casual", "Indie", "Strategy", "Simulation", "Multiplayer", "Singleplayer", "Co-op", "Early Access", "Open World", "Story Rich", "Puzzle", "Platformer", "Shooter", "FPS", "Third Person", "VR", "Fantasy", "Sci-fi", "Horror", "Survival", "Anime", "Visual Novel", "Pixel Graphics", "Turn-Based", "Card Game", "Sandbox", "Building", "Crafting", "Mystery", "Comedy", "Dark", "Gore", "Violent", "Cute", "Psychological Horror", "Detective", "Zombies", "Online Co-Op", "Hack and Slash", "Fighting", "Beat 'em up", "Bullet Hell", "Side Scroller", "Roguelike", "Roguelite", "Action RPG", "Action-Adventure", "MMO", "JRPG", "Dungeon Crawler", "Match 3", "Sports", "Racing", "First-Person", "Third-Person Shooter", "Rhythm", "Music", "Management", "Clicker", "Tactical", "Stealth", "Exploration"]

        Example 1:
        Input sentence: Games like League of Legends
        Output: {"tags": ["MOBA", "Multiplayer", "Real-Time"]}

        Example 2:
        Input sentence: Recommend roguelike games that I can enjoy with friends
        Output: {"tags": ["Roguelike", "Multiplayer", "Co-op"]}

        Example 3:
        Input sentence: Emotional story games I can play alone quietly and immersively
        Output: {"tags": ["Singleplayer", "Story Rich", "Adventure"]}

        Example 4:
        Input sentence: Pixel graphics roguelike games good for Steam Deck
        Output: {"tags": ["Pixel Graphics", "Roguelike", "Singleplayer"]}

        Example 5:
        Input sentence: Challenging games I can play all night with friends
        Output: {"tags": ["Co-op", "Roguelike", "Difficult"]}

        Extract tags from the Steam tag list in this manner and output them.

        Input sentence: %s
        Output:
        """.formatted(input);
    }
}
