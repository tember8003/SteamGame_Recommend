package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.StorefrontDetail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

@Slf4j
@Component
public class StorefrontParser {

    // cc=us&l=english 응답의 날짜 포맷, 예: "Jul 9, 2013"
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private final ObjectMapper objectMapper;

    public StorefrontParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<StorefrontDetail> parse(long appid, String json) {
        if (json == null || json.isBlank()) return Optional.empty();
        try {
            JsonNode node = findEntry(objectMapper.readTree(json), appid);
            if (node == null) return Optional.empty();
            if (!node.path("success").asBoolean(false)) return Optional.empty();
            JsonNode data = node.path("data");

            String name = text(data, "name");
            if (name == null || name.isBlank()) return Optional.empty();

            String description = text(data, "short_description");
            String imageUrl = trunc(text(data, "header_image"), 255);
            boolean isFree = data.path("is_free").asBoolean(false);
            boolean korean = data.path("supported_languages").asText("").contains("Korean");
            String genres = trunc(joinGenres(data.path("genres")), 500);
            Integer price = parsePrice(data.path("price_overview"));
            LocalDate release = parseReleaseDate(data.path("release_date"));
            String developer = trunc(firstOf(data.path("developers")), 255);
            String publisher = trunc(firstOf(data.path("publishers")), 255);

            return Optional.of(new StorefrontDetail(appid, trunc(name, 255), description, imageUrl,
                    isFree, korean, genres, price, release, developer, publisher));
        } catch (Exception ex) {
            log.error("Failed to parse storefront appdetails for appid {}", appid, ex);
            return Optional.empty();
        }
    }

    // Steam 이 바깥 키를 요청 appid 가 아닌 값으로 주는 경우가 있어 data.steam_appid 로 먼저 찾는다
    // 키가 appid 와 같아도 steam_appid 가 다른 게임을 가리키면 그 게임 정보라 버린다, steam_appid 가 없는 옛 형식만 키로 받는다
    private static JsonNode findEntry(JsonNode root, long appid) {
        if (root == null || !root.isObject()) return null;
        for (var entry : root.properties()) {
            Long steamAppid = steamAppidOf(entry.getValue());
            if (steamAppid != null && steamAppid == appid) {
                return entry.getValue();
            }
        }
        JsonNode byKey = root.get(String.valueOf(appid));
        if (byKey != null && steamAppidOf(byKey) != null) {
            byKey = null;
        }
        if (byKey == null && !root.isEmpty()) {
            log.warn("appdetails 응답에서 appid {} 항목을 찾지 못함, 받은 키 {}", appid,
                    root.properties().iterator().next().getKey());
        }
        return byKey;
    }

    // data.steam_appid 를 숫자와 숫자 문자열 모두에서 읽는다, 없거나 못 읽으면 null
    private static Long steamAppidOf(JsonNode entry) {
        JsonNode n = entry.path("data").path("steam_appid");
        if (n.isNumber()) return n.asLong();
        if (n.isTextual()) {
            try {
                return Long.parseLong(n.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String text(JsonNode data, String field) {
        JsonNode n = data.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asText();
    }

    private static String joinGenres(JsonNode genres) {
        if (!genres.isArray() || genres.isEmpty()) return null;
        StringJoiner sj = new StringJoiner(", ");
        genres.forEach(g -> { String d = g.path("description").asText(""); if (!d.isBlank()) sj.add(d); });
        String out = sj.toString();
        return out.isBlank() ? null : out;
    }

    private static Integer parsePrice(JsonNode priceOverview) {
        if (priceOverview.isMissingNode() || priceOverview.isNull()) return null;
        JsonNode fin = priceOverview.path("final");
        return fin.isMissingNode() || fin.isNull() ? null : fin.asInt();
    }

    private static LocalDate parseReleaseDate(JsonNode releaseDate) {
        if (releaseDate.path("coming_soon").asBoolean(false)) return null;
        String date = releaseDate.path("date").asText("");
        if (date.isBlank()) return null;
        try { return LocalDate.parse(date, DATE_FMT); }
        catch (Exception e) { return null; }   // "Q1 2027", "2013" 등 비정형/로컬라이즈 포맷은 null 처리
    }

    private static String firstOf(JsonNode arr) {
        if (!arr.isArray() || arr.isEmpty()) return null;
        String v = arr.get(0).asText("");
        return v.isBlank() ? null : v;
    }

    private static String trunc(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
