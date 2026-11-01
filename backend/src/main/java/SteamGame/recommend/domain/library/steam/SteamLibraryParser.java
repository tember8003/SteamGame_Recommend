package SteamGame.recommend.domain.library.steam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Steam 응답 문자열을 해석하는 순수 함수 모음
public final class SteamLibraryParser {

    private static final Logger log = LoggerFactory.getLogger(SteamLibraryParser.class);
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

    // 응답 모양이 기대와 다르면 사용자 오류로 오해하지 않게 장애로 본다, 없는 사용자는 players가 빈 배열일 때만이다
    public static SummaryParse parseSummary(String json) {
        try {
            JsonNode response = MAPPER.readTree(json).path("response");
            if (!response.isObject()) {
                return unavailableSummary("SUMMARY_NO_RESPONSE");
            }
            JsonNode players = response.path("players");
            if (!players.isArray()) {
                return unavailableSummary("SUMMARY_NO_PLAYERS");
            }
            if (players.isEmpty()) {
                return new SummaryParse(SummaryStatus.NOT_FOUND, null, null);
            }
            JsonNode player = players.get(0);
            if (!player.path("communityvisibilitystate").isInt()) {
                return unavailableSummary("SUMMARY_NO_VISIBILITY");
            }
            if (player.path("communityvisibilitystate").asInt() != VISIBILITY_PUBLIC) {
                return new SummaryParse(SummaryStatus.PRIVATE, null, null);
            }
            SteamProfile profile = new SteamProfile(
                    player.path("steamid").asText(""),
                    player.path("personaname").asText(""),
                    player.path("avatarfull").asText(""));
            return new SummaryParse(SummaryStatus.PUBLIC, profile, null);
        } catch (Exception e) {
            return unavailableSummary("SUMMARY_PARSE_FAILED");
        }
    }

    private static SummaryParse unavailableSummary(String reason) {
        return new SummaryParse(SummaryStatus.UNAVAILABLE, null, reason);
    }

    public static GamesParse parseOwnedGames(String json) {
        try {
            JsonNode response = MAPPER.readTree(json).path("response");
            JsonNode games = response.path("games");
            JsonNode count = response.path("game_count");
            // 둘 다 없으면 게임 세부 정보가 비공개라는 뜻
            if (!games.isArray() && !response.has("game_count")) {
                return new GamesParse(GamesStatus.PRIVATE, null, null);
            }
            // game_count가 숫자가 아니면 응답이 어긋난 것이라 비공개나 빈 라이브러리로 오해하지 않고 장애로 봄
            if (response.has("game_count") && !count.isNumber()) {
                return new GamesParse(GamesStatus.UNAVAILABLE, null, "OWNED_GAMES_BAD_COUNT");
            }
            // 게임이 있다고 하면서 목록이 없거나 비어 있으면 응답이 어긋난 것이라 장애로 봄
            if ((!games.isArray() || games.isEmpty()) && count.asInt() > 0) {
                return new GamesParse(GamesStatus.UNAVAILABLE, null, "OWNED_GAMES_INCONSISTENT");
            }
            if (!games.isArray() || games.isEmpty() || (count.isNumber() && count.asInt() == 0)) {
                return new GamesParse(GamesStatus.EMPTY, null, null);
            }
            List<OwnedGame> result = new ArrayList<>(games.size());
            int skipped = 0;
            for (JsonNode g : games) {
                long appid = g.path("appid").asLong(0);
                // appid가 없으면 태그를 붙일 수 없고 형식이 바뀐 신호일 수 있어 건너뛴다
                if (appid <= 0) {
                    skipped++;
                    continue;
                }
                long lastPlayed = g.path("rtime_last_played").asLong(0);
                result.add(new OwnedGame(
                        appid,
                        g.path("name").asText(""),
                        g.path("playtime_forever").asInt(0),
                        g.path("playtime_2weeks").asInt(0),
                        lastPlayed > 0 ? Instant.ofEpochSecond(lastPlayed) : null));
            }
            if (skipped > 0) {
                log.warn("Steam owned games entries without appid skipped: skipped={}, total={}", skipped, games.size());
            }
            // 절반을 넘게 버렸다면 응답 형식이 바뀐 것으로 보고 장애로 처리함
            if (skipped * 2 > games.size()) {
                return new GamesParse(GamesStatus.UNAVAILABLE, null, "OWNED_GAMES_MOSTLY_INVALID");
            }
            return new GamesParse(GamesStatus.FOUND, List.copyOf(result), null);
        } catch (Exception e) {
            return new GamesParse(GamesStatus.UNAVAILABLE, null, "OWNED_GAMES_PARSE_FAILED");
        }
    }

    // 예상 밖 응답 형태, reason은 로그에 그대로 남는다
    static final class UnexpectedResponseException extends Exception {
        private final String reason;

        UnexpectedResponseException(String reason) {
            super(reason);
            this.reason = reason;
        }

        String reason() {
            return reason;
        }
    }

    // success가 1이면 steamid, 42면 null(없는 이름), 그 밖의 값이나 깨진 응답은 예외
    static String parseVanity(String json) throws Exception {
        JsonNode response = MAPPER.readTree(json).path("response");
        int success = response.path("success").asInt(0);
        if (success == 1) {
            if (!response.path("steamid").isTextual()) {
                throw new UnexpectedResponseException("VANITY_NO_STEAMID");
            }
            return response.path("steamid").asText();
        }
        if (success == 42) {
            return null;
        }
        throw new UnexpectedResponseException("VANITY_UNEXPECTED_SUCCESS");
    }
}
