package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.StorefrontDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontParserTest {

    final StorefrontParser parser = new StorefrontParser(new ObjectMapper());

    @Test
    void parsesConfirmedFields_freeGame() {
        String json = """
            {"570":{"success":true,"data":{
              "name":"Dota 2","short_description":"The most-played game.",
              "header_image":"https://img/570.jpg","is_free":true,
              "supported_languages":"English<strong>*</strong>, Korean, French",
              "genres":[{"id":"1","description":"Action"},{"id":"2","description":"Strategy"}],
              "price_overview":null,
              "release_date":{"coming_soon":false,"date":"Jul 9, 2013"},
              "developers":["Valve"],"publishers":["Valve"]}}}
            """;

        StorefrontDetail d = parser.parse(570L, json).orElseThrow();

        assertThat(d.name()).isEqualTo("Dota 2");
        assertThat(d.description()).isEqualTo("The most-played game.");
        assertThat(d.imageUrl()).isEqualTo("https://img/570.jpg");
        assertThat(d.isFree()).isTrue();
        assertThat(d.koreanSupport()).isTrue();
        assertThat(d.genres()).isEqualTo("Action, Strategy");
        assertThat(d.price()).isNull();
        assertThat(d.releaseDate()).isEqualTo(LocalDate.of(2013, 7, 9));
        assertThat(d.developer()).isEqualTo("Valve");
        assertThat(d.publisher()).isEqualTo("Valve");
    }

    @Test
    void parsesPriceCents_andNoKorean() {
        String json = """
            {"10":{"success":true,"data":{
              "name":"Paid Game","is_free":false,
              "supported_languages":"English, French",
              "price_overview":{"final":1999,"currency":"USD"},
              "release_date":{"coming_soon":false,"date":"Nov 8, 2004"}}}}
            """;

        StorefrontDetail d = parser.parse(10L, json).orElseThrow();

        assertThat(d.price()).isEqualTo(1999);
        assertThat(d.koreanSupport()).isFalse();
        assertThat(d.isFree()).isFalse();
    }

    @Test
    void unparseableOrComingSoonDate_yieldsNullReleaseDate() {
        String comingSoon = "{\"1\":{\"success\":true,\"data\":{\"name\":\"X\",\"release_date\":{\"coming_soon\":true,\"date\":\"Q1 2027\"}}}}";
        assertThat(parser.parse(1L, comingSoon).orElseThrow().releaseDate()).isNull();
    }

    @Test
    void outerKeyDiffersFromAppid_butSteamAppidMatches_parses() {
        // Steam 이 바깥 키를 요청 appid 가 아닌 값으로 주는 경우, data.steam_appid 로 찾아야 함
        String json = """
            {"3494100":{"success":true,"data":{
              "steam_appid":1172470,"name":"Apex Legends",
              "header_image":"https://img/1172470.jpg","is_free":true}}}
            """;

        StorefrontDetail d = parser.parse(1172470L, json).orElseThrow();

        assertThat(d.name()).isEqualTo("Apex Legends");
        assertThat(d.imageUrl()).isEqualTo("https://img/1172470.jpg");
    }

    @Test
    void outerKeyAndSteamAppidBothPointElsewhere_yieldsEmpty() {
        // 다른 게임을 가리키는 응답이면 그 게임 정보를 이 appid 에 저장하지 않음
        String json = "{\"999\":{\"success\":true,\"data\":{\"steam_appid\":555,\"name\":\"Other\"}}}";

        assertThat(parser.parse(111L, json)).isEmpty();
    }

    @Test
    void sameOuterKeyButSteamAppidPointsElsewhere_yieldsEmpty() {
        // 키가 요청 appid 와 같아도 data.steam_appid 가 다른 게임이면 그 게임 정보를 저장하지 않음
        String json = "{\"111\":{\"success\":true,\"data\":{\"steam_appid\":999,\"name\":\"Other\"}}}";

        assertThat(parser.parse(111L, json)).isEmpty();
    }

    @Test
    void steamAppidAsString_stillMatches() {
        String json = "{\"5\":{\"success\":true,\"data\":{\"steam_appid\":\"42\",\"name\":\"Quoted\"}}}";

        assertThat(parser.parse(42L, json).orElseThrow().name()).isEqualTo("Quoted");
    }

    @Test
    void successFalse_orBlankName_yieldsEmpty() {
        assertThat(parser.parse(99L, "{\"99\":{\"success\":false}}")).isEmpty();
        assertThat(parser.parse(99L, "{\"99\":{\"success\":true,\"data\":{\"name\":\"\"}}}")).isEmpty();
        assertThat(parser.parse(99L, "")).isEmpty();
        assertThat(parser.parse(99L, "garbage")).isEmpty();
    }
}
