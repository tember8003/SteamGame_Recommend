package SteamGame.recommend.domain.library.steam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Steam 응답 문자열을 해석하는 순수 함수 모음
public final class SteamLibraryParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int VISIBILITY_PUBLIC = 3;

    private SteamLibraryParser() {
    }

    public enum SummaryStatus { PUBLIC, PRIVATE, NOT_FOUND, UNAVAILABLE }

    public enum GamesStatus { FOUND, PRIVATE, EMPTY, UNAVAILABLE }

    // profile은 PUBLIC일 때만, reason은 UNAVAILABLE일 때만 채워짐
    public record SummaryParse(SummaryStatus status, SteamProfile profile, String reason) {
    }

    // games는 FOUND일 때만, reason은 UNAVAILABLE일 때만 채워짐
    public record GamesParse(GamesStatus status, List<OwnedGame> games, String reason) {
    }

    public static SummaryParse parseSummary(String json) {
        try {
            JsonNode players = MAPPER.readTree(json).path("response").path("players");
            if (!players.isArray() || players.isEmpty()) {
                return new SummaryParse(SummaryStatus.NOT_FOUND, null, null);
            }
            JsonNode player = players.get(0);
            if (player.path("communityvisibilitystate").asInt(0) != VISIBILITY_PUBLIC) {
                return new SummaryParse(SummaryStatus.PRIVATE, null, null);
            }
            SteamProfile profile = new SteamProfile(
                    player.path("steamid").asText(""),
                    player.path("personaname").asText(""),
                    player.path("avatarfull").asText(""));
            return new SummaryParse(SummaryStatus.PUBLIC, profile, null);
        } catch (Exception e) {
            return new SummaryParse(SummaryStatus.UNAVAILABLE, null, "SUMMARY_PARSE_FAILED");
        }
    }

    public static GamesParse parseOwnedGames(String json) {
        try {
            JsonNode response = MAPPER.readTree(json).path("response");
            JsonNode games = response.path("games");
            JsonNode count = response.path("game_count");
            // 둘 다 없으면 게임 세부 정보가 비공개라는 뜻
            if (!games.isArray() && !count.isNumber()) {
                return new GamesParse(GamesStatus.PRIVATE, null, null);
            }
            // 게임이 있다고 하면서 목록이 없으면 응답이 어긋난 것이라 장애로 봄
            if (!games.isArray() && count.asInt() > 0) {
                return new GamesParse(GamesStatus.UNAVAILABLE, null, "OWNED_GAMES_INCONSISTENT");
            }
            if (!games.isArray() || games.isEmpty() || (count.isNumber() && count.asInt() == 0)) {
                return new GamesParse(GamesStatus.EMPTY, null, null);
            }
            List<OwnedGame> result = new ArrayList<>(games.size());
            for (JsonNode g : games) {
                long lastPlayed = g.path("rtime_last_played").asLong(0);
                result.add(new OwnedGame(
                        g.path("appid").asLong(0),
                        g.path("name").asText(""),
                        g.path("playtime_forever").asInt(0),
                        g.path("playtime_2weeks").asInt(0),
                        lastPlayed > 0 ? Instant.ofEpochSecond(lastPlayed) : null));
            }
            return new GamesParse(GamesStatus.FOUND, List.copyOf(result), null);
        } catch (Exception e) {
            return new GamesParse(GamesStatus.UNAVAILABLE, null, "OWNED_GAMES_PARSE_FAILED");
        }
    }

    // success가 1이면 steamid, 아니면 null, 깨진 응답은 예외
    static String parseVanity(String json) throws Exception {
        JsonNode response = MAPPER.readTree(json).path("response");
        if (response.path("success").asInt(0) == 1 && response.path("steamid").isTextual()) {
            return response.path("steamid").asText();
        }
        return null;
    }
}
