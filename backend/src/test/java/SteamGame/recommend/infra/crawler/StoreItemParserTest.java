package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.AppTag;
import SteamGame.recommend.infra.crawler.dto.StoreItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreItemParserTest {

    final StoreItemParser parser = new StoreItemParser(new ObjectMapper());

    static final Map<Integer, String> TAGS = Map.of(
            87918, "Farming Sim", 3964, "Pixel Graphics", 122, "RPG", 597, "Casual");

    static final String TAG_LIST = """
            {"response":{"version_hash":"1","tags":[
              {"tagid":87918,"name":"Farming Sim"},{"tagid":122,"name":"RPG"},{"tagid":9,"name":""}]}}
            """;

    static final String FULL_ITEM = """
            {"appid":413150,"name":"Stardew Valley",
             "tags":[{"tagid":87918,"weight":859},{"tagid":3964,"weight":788},{"tagid":122,"weight":685}],
             "reviews":{"summary_filtered":{"review_count":895314,"percent_positive":98,"review_score":9,
                        "review_score_label":"Overwhelmingly Positive"}},
             "basic_info":{"short_description":"You've inherited your grandfather's old farm plot.",
                           "publishers":[{"name":"ConcernedApe"}],"developers":[{"name":"ConcernedApe"}]},
             "assets":{"asset_url_format":"steam/apps/413150/${FILENAME}?t=1786554168","header":"header.jpg"},
             "release":{"steam_release_date":1456444800},
             "best_purchase_option":{"final_price_in_cents":"1499"},
             "supported_languages":[{"elanguage":0,"supported":true},{"elanguage":4,"supported":true}]}
            """;

    static String page(String... items) {
        return "{\"response\":{\"metadata\":{\"total_matching_records\":130000},\"store_items\":["
                + String.join(",", items) + "]}}";
    }

    @Test
    void parseTagList_mapsIdToName_skipsBlankNames() {
        Map<Integer, String> m = parser.parseTagList(TAG_LIST);
        assertThat(m).containsEntry(87918, "Farming Sim").containsEntry(122, "RPG").doesNotContainKey(9);
    }

    @Test
    void parseTagList_throwsOnGarbage_returnsEmptyOnNullOrBlank() {
        assertThatThrownBy(() -> parser.parseTagList("not json")).isInstanceOf(IllegalStateException.class);
        assertThat(parser.parseTagList(null)).isEmpty();
        assertThat(parser.parseTagList("  ")).isEmpty();
    }

    @Test
    void parsePage_fullItem_mapsEveryColumn() {
        StoreItemParser.QueryPage p = parser.parsePage(page(FULL_ITEM), TAGS);

        assertThat(p.rawCount()).isEqualTo(1);
        StoreItem s = p.items().get(0);
        assertThat(s.appid()).isEqualTo(413150L);
        assertThat(s.name()).isEqualTo("Stardew Valley");
        assertThat(s.description()).isEqualTo("You've inherited your grandfather's old farm plot.");
        assertThat(s.imageUrl()).isEqualTo(
                "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/413150/header.jpg?t=1786554168");
        assertThat(s.isFree()).isFalse();
        assertThat(s.koreanSupport()).isTrue();
        assertThat(s.price()).isEqualTo(1499);
        assertThat(s.releaseDate()).isEqualTo(LocalDate.of(2016, 2, 26));
        assertThat(s.developer()).isEqualTo("ConcernedApe");
        assertThat(s.publisher()).isEqualTo("ConcernedApe");
        assertThat(s.reviewCount()).isEqualTo(895314);
        assertThat(s.positivePercentage()).isEqualByComparingTo(new BigDecimal("98.00"));
        assertThat(s.reviewScore()).isEqualTo("Overwhelmingly Positive");
        assertThat(s.tags()).containsExactly(
                new AppTag("Farming Sim", 859), new AppTag("Pixel Graphics", 788), new AppTag("RPG", 685));
        assertThat(s.genres()).isEqualTo("RPG");
    }

    @Test
    void parsePage_missingFields_leavesThemNull_andFreeGameHasNoPrice() {
        String bare = """
                {"appid":730,"name":"Counter-Strike 2","is_free":true,
                 "supported_languages":[{"elanguage":4,"supported":false}]}
                """;
        StoreItem s = parser.parsePage(page(bare), TAGS).items().get(0);

        assertThat(s.isFree()).isTrue();
        assertThat(s.price()).isNull();
        assertThat(s.description()).isNull();
        assertThat(s.imageUrl()).isNull();
        assertThat(s.releaseDate()).isNull();
        assertThat(s.developer()).isNull();
        assertThat(s.publisher()).isNull();
        assertThat(s.reviewCount()).isZero();
        assertThat(s.positivePercentage()).isNull();
        assertThat(s.reviewScore()).isNull();
        assertThat(s.koreanSupport()).isFalse();   // supported=false 는 지원으로 치지 않는다
        assertThat(s.tags()).isEmpty();
        assertThat(s.genres()).isNull();
    }

    @Test
    void parsePage_noLanguagesField_givesNullKoreanSupport() {
        StoreItem s = parser.parsePage(page("{\"appid\":731,\"name\":\"NoLang\"}"), TAGS).items().get(0);
        assertThat(s.koreanSupport()).isNull();
    }

    @Test
    void parsePage_languagesWithoutKorean_givesFalse() {
        String item = "{\"appid\":732,\"name\":\"EnOnly\",\"supported_languages\":[{\"elanguage\":0,\"supported\":true}]}";
        StoreItem s = parser.parsePage(page(item), TAGS).items().get(0);
        assertThat(s.koreanSupport()).isFalse();
    }

    @Test
    void parsePage_fillsTotalMatching() {
        assertThat(parser.parsePage(page(FULL_ITEM), TAGS).totalMatching()).isEqualTo(130000);
        assertThat(parser.parsePage("{\"response\":{\"store_items\":[]}}", TAGS).totalMatching()).isZero();
        // 결과 끝 페이지는 store_items 없이 metadata 만 올 수 있음
        StoreItemParser.QueryPage end = parser.parsePage(
                "{\"response\":{\"metadata\":{\"total_matching_records\":130000}}}", TAGS);
        assertThat(end.rawCount()).isZero();
        assertThat(end.totalMatching()).isEqualTo(130000);
    }

    @Test
    void parsePage_unknownTagId_emptiesTagsSoOldTagsSurvive_andCounts() {
        String item = """
                {"appid":1,"name":"G","tags":[{"tagid":597,"weight":10},{"tagid":424242,"weight":9}]}
                """;
        StoreItemParser.QueryPage p = parser.parsePage(page(item), TAGS);
        StoreItem s = p.items().get(0);
        assertThat(s.tags()).isEmpty();
        assertThat(s.genres()).isNull();
        assertThat(p.unknownTags()).isEqualTo(1);
    }

    @Test
    void parsePage_noUnknownTags_countsZero() {
        assertThat(parser.parsePage(page(FULL_ITEM), TAGS).unknownTags()).isZero();
    }

    @Test
    void parsePage_itemWithoutNameOrAppid_isSkipped_butCountedAsRaw() {
        String noName = "{\"appid\":2}";
        String noAppid = "{\"name\":\"X\"}";
        StoreItemParser.QueryPage p = parser.parsePage(page(noName, noAppid, FULL_ITEM), TAGS);
        assertThat(p.rawCount()).isEqualTo(3);
        assertThat(p.items()).extracting(StoreItem::appid).containsExactly(413150L);
    }

    @Test
    void parsePage_truncatesLongColumns() {
        String longName = "N".repeat(300);
        String item = "{\"appid\":3,\"name\":\"" + longName + "\","
                + "\"basic_info\":{\"developers\":[{\"name\":\"" + "D".repeat(300) + "\"}]}}";
        StoreItem s = parser.parsePage(page(item), TAGS).items().get(0);
        assertThat(s.name()).hasSize(255);
        assertThat(s.developer()).hasSize(255);
    }

    @Test
    void parsePage_emptyItems_returnsZeroRaw() {
        assertThat(parser.parsePage(page(), TAGS).rawCount()).isZero();
        assertThat(parser.parsePage("{\"response\":{}}", TAGS).rawCount()).isZero();
    }

    @Test
    void parsePage_garbage_throws() {
        assertThatThrownBy(() -> parser.parsePage("<html>oops</html>", TAGS))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void parsePage_blankBody_throws() {
        assertThatThrownBy(() -> parser.parsePage("", TAGS)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> parser.parsePage(null, TAGS)).isInstanceOf(IllegalStateException.class);
    }
}
