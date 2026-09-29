package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.AppTag;
import SteamGame.recommend.infra.crawler.dto.StoreItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

@Slf4j
@Component
public class StoreItemParser {

    static final String IMAGE_BASE = "https://shared.akamai.steamstatic.com/store_item_assets/";
    static final int ELANGUAGE_KOREAN = 4;
    // Query 는 장르를 주지 않아서 태그 중 스팀 장르와 이름이 같은 것만 장르로 씀
    static final List<String> GENRE_TAGS = List.of(
            "Action", "Adventure", "Casual", "Indie", "RPG", "Simulation", "Strategy",
            "Sports", "Racing", "Massively Multiplayer", "Free to Play", "Early Access");

    // totalMatching 은 응답 metadata 값이고 없으면 0으로 둠, unknownTags 는 이 페이지에서 이름을 못 찾은 태그 항목 수를 셈
    public record QueryPage(int rawCount, List<StoreItem> items, int totalMatching, int unknownTags) {
        public QueryPage {
            items = List.copyOf(items);
        }
    }

    private final ObjectMapper objectMapper;

    public StoreItemParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // GetTagList 응답을 tagid → 이름 맵으로 바꿈, 입력이 비면 빈 맵을 주고 읽지 못하면 예외를 던짐
    public Map<Integer, String> parseTagList(String json) {
        Map<Integer, String> out = new HashMap<>();
        if (json == null || json.isBlank()) return out;
        try {
            for (JsonNode t : objectMapper.readTree(json).path("response").path("tags")) {
                String name = t.path("name").asText("");
                if (t.hasNonNull("tagid") && !name.isBlank()) out.put(t.path("tagid").asInt(), name);
            }
        } catch (Exception e) {
            throw new IllegalStateException("GetTagList 응답을 읽지 못함", e);
        }
        return out;
    }

    // rawCount 는 파싱 성공 여부와 무관하게 응답에 들어 있던 게임 수라 끝 페이지 판단에 씀
    public QueryPage parsePage(String json, Map<Integer, String> tagNames) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("Query 응답이 비어 있음");
        }
        JsonNode response;
        try {
            response = objectMapper.readTree(json).path("response");
        } catch (Exception e) {
            throw new IllegalStateException("Query 응답을 읽지 못함", e);
        }
        // 결과 끝 페이지는 store_items 없이 metadata 만 올 수 있어 전체 수를 먼저 읽음
        int totalMatching = response.path("metadata").path("total_matching_records").asInt(0);
        JsonNode items = response.path("store_items");
        if (!items.isArray()) return new QueryPage(0, List.of(), totalMatching, 0);
        List<StoreItem> out = new ArrayList<>();
        Set<Integer> unknownIds = new LinkedHashSet<>();
        int[] unknownCount = {0};
        for (JsonNode it : items) {
            try {
                parseItem(it, tagNames, unknownIds, unknownCount).ifPresent(out::add);
            } catch (Exception e) {
                log.warn("store item parse failed for appid {}, skipping", it.path("appid").asText("?"), e);
            }
        }
        if (unknownCount[0] > 0) {
            log.warn("unknown tagid {} entries on this page, ids(up to 10)={}",
                    unknownCount[0], unknownIds.stream().limit(10).toList());
        }
        return new QueryPage(items.size(), out, totalMatching, unknownCount[0]);
    }

    private Optional<StoreItem> parseItem(JsonNode it, Map<Integer, String> tagNames,
                                          Set<Integer> unknownIds, int[] unknownCount) {
        long appid = it.path("appid").asLong(0);
        String name = text(it, "name");
        if (appid <= 0 || name == null || name.isBlank()) {
            log.debug("store item rejected, appid={}", it.path("appid").asText("?"));
            return Optional.empty();
        }

        List<AppTag> tags = new ArrayList<>();
        boolean hasUnknown = false;
        for (JsonNode t : it.path("tags")) {
            int tagid = t.path("tagid").asInt(-1);
            String tagName = tagNames.get(tagid);
            if (tagName == null) {
                hasUnknown = true;
                unknownCount[0]++;
                unknownIds.add(tagid);
                continue;
            }
            tags.add(new AppTag(trunc(tagName, 255), t.path("weight").asInt(0)));
        }
        // 알 수 없는 태그가 하나라도 있으면 짧아진 목록으로 덮지 않도록 비워서 기존 태그를 지킴
        if (hasUnknown) tags = List.of();

        JsonNode basic = it.path("basic_info");
        JsonNode rev = it.path("reviews").path("summary_filtered");
        BigDecimal pct = rev.hasNonNull("percent_positive")
                ? BigDecimal.valueOf(rev.path("percent_positive").asInt()).setScale(2, RoundingMode.UNNECESSARY)
                : null;

        return Optional.of(new StoreItem(
                appid,
                trunc(name, 255),
                text(basic, "short_description"),
                trunc(imageUrl(it.path("assets")), 255),
                it.path("is_free").asBoolean(false),
                koreanSupported(it.path("supported_languages")),
                trunc(genres(tags), 500),
                price(appid, it.path("best_purchase_option")),
                releaseDate(it.path("release")),
                trunc(firstName(basic.path("developers")), 255),
                trunc(firstName(basic.path("publishers")), 255),
                rev.path("review_count").asInt(0),
                pct,
                trunc(text(rev, "review_score_label"), 50),
                tags));
    }

    private static String imageUrl(JsonNode assets) {
        String fmt = text(assets, "asset_url_format");
        String header = text(assets, "header");
        if (fmt == null || header == null) return null;
        return IMAGE_BASE + fmt.replace("${FILENAME}", header);
    }

    // 언어 목록이 없으면 null 이라 저장소가 기존 값을 지킴
    private static Boolean koreanSupported(JsonNode langs) {
        if (!langs.isArray()) return null;
        for (JsonNode l : langs) {
            if (l.path("elanguage").asInt(-1) == ELANGUAGE_KOREAN && l.path("supported").asBoolean(false)) return true;
        }
        return false;
    }

    private static String genres(List<AppTag> tags) {
        StringJoiner sj = new StringJoiner(", ");
        for (AppTag t : tags) if (GENRE_TAGS.contains(t.name())) sj.add(t.name());
        return sj.length() == 0 ? null : sj.toString();
    }

    private static Integer price(long appid, JsonNode option) {
        String cents = text(option, "final_price_in_cents");
        if (cents == null || cents.isBlank()) return null;
        try {
            return Integer.parseInt(cents);
        } catch (NumberFormatException e) {
            log.warn("price parse failed for appid {}, raw={}", appid, cents);
            return null;
        }
    }

    private static LocalDate releaseDate(JsonNode release) {
        long sec = release.path("steam_release_date").asLong(0);
        if (sec <= 0) return null;
        return LocalDate.ofInstant(Instant.ofEpochSecond(sec), ZoneOffset.UTC);
    }

    private static String firstName(JsonNode arr) {
        if (!arr.isArray() || arr.isEmpty()) return null;
        return text(arr.get(0), "name");
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static String trunc(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
