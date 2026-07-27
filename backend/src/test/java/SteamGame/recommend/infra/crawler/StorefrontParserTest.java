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
    void successFalse_orBlankName_yieldsEmpty() {
        assertThat(parser.parse(99L, "{\"99\":{\"success\":false}}")).isEmpty();
        assertThat(parser.parse(99L, "{\"99\":{\"success\":true,\"data\":{\"name\":\"\"}}}")).isEmpty();
        assertThat(parser.parse(99L, "")).isEmpty();
        assertThat(parser.parse(99L, "garbage")).isEmpty();
    }
}
