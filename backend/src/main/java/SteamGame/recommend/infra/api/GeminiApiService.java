package SteamGame.recommend.infra.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GeminiApiService {
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private final String gemini_api_key;
    private final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    public GeminiApiService(ObjectMapper objectMapper,WebClient.Builder webClientBuilder, @Value("${spring.ai.google.api-key}") String gemini_api_key){
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
        this.gemini_api_key = gemini_api_key;
    }

    @Retry(name="geminiApi", fallbackMethod = "fallbackGEMINI")
    @CircuitBreaker(name="geminiApi",fallbackMethod = "fallbackGEMINI")
    public List<String> getGeminiTags(String input) {

        String prompt = buildPrompt(input);

        Map<String,Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature",1, "maxOutputTokens",8192)
        );

        String raw = webClientBuilder.build()
                .post()
                .uri(GEMINI_URL + "?key=" + gemini_api_key)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        Mono.error(new ResponseStatusException(resp.statusCode(), "Gemini API 오류"))
                )
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .block();

        try {
            JsonNode root = objectMapper.readTree(raw);
            String text = root.path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();

            Matcher m = Pattern.compile("\\[.*?\\]", Pattern.DOTALL).matcher(text);

            if (m.find()) {
                String jsonArray = m.group();
                List<String> tags = objectMapper.readValue(
                        jsonArray, new TypeReference<List<String>>() {}
                );
                return tags.stream().limit(5).toList();
            }
        } catch (Exception e) {
            log.error("Gemini Tag parsing failed", e);
        }

        return Collections.emptyList();
    }

    private String buildPrompt(String input) {
        return """
        Analyze the following sentence and extract up to 5 related tags from the tag list below. Output them in JSON array format.
        
        Tag list:
        ["2D", "3D", "RPG", "Action", "Roguelike", "Adventure", "Casual", "Indie", "Strategy", "Simulation", "Multiplayer", "Singleplayer", "Co-op", "Early Access", "Open World", "Story Rich", "Puzzle", "Platformer", "Shooter", "FPS", "Third Person", "VR", "Fantasy", "Sci-fi", "Horror", "Survival", "Anime", "Visual Novel", "Pixel Graphics", "Turn-Based", "Card Game", "Sandbox", "Building", "Crafting", "Mystery", "Comedy", "Dark", "Gore", "Violent", "Cute", "Psychological Horror", "Detective", "Zombies", "Online Co-Op", "Hack and Slash", "Fighting", "Beat 'em up", "Bullet Hell", "Side Scroller", "Roguelike", "Roguelite", "Action RPG", "Action-Adventure", "MMO", "JRPG", "Dungeon Crawler", "Match 3", "Sports", "Racing", "First-Person", "Third-Person Shooter", "Rhythm", "Music", "Management", "Clicker", "Tactical", "Stealth", "Exploration"]
        
        Example 1:
        Input sentence: Games like League of Legends
        Output: ["MOBA", "Multiplayer", "Real-Time"]
        
        Example 2:
        Input sentence: Recommend roguelike games that I can enjoy with friends
        Output: ["Roguelike", "Multiplayer", "Co-op"]
        
        Example 3:
        Input sentence: Emotional story games I can play alone quietly and immersively
        Output: ["Singleplayer", "Story Rich", "Adventure"]
        
        Example 4:
        Input sentence: Pixel graphics roguelike games good for Steam Deck
        Output: ["Pixel Graphics", "Roguelike", "Singleplayer"]
        
        Example 5:
        Input sentence: Challenging games I can play all night with friends
        Output: ["Co-op", "Roguelike", "Difficult"]
       
        Extract tags from the Steam tag list in this manner and output them.
        
        Input sentence: %s
        Output:
        """.formatted(input);
    }

    public List<String> fallbackGEMINI(String input,Throwable ex){
        log.info("GEMINI API Synchronization failure (input={}): {}", input, ex.getMessage());

        return Collections.emptyList();
    }
}
