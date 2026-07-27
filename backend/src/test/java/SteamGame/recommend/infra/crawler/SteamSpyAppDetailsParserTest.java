package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.AppTag;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SteamSpyAppDetailsParserTest {

    final SteamSpyAppDetailsParser parser = new SteamSpyAppDetailsParser(new ObjectMapper());

    @Test
    void parsesTagObject_intoNameVotePairs() {
        String json = "{\"appid\":570,\"name\":\"Dota 2\",\"tags\":{\"MOBA\":20225,\"Multiplayer\":15411,\"Strategy\":14289}}";

        List<AppTag> tags = parser.parseTags(json);

        assertThat(tags).extracting(AppTag::name).containsExactlyInAnyOrder("MOBA", "Multiplayer", "Strategy");
        assertThat(tags).filteredOn(t -> t.name().equals("MOBA")).singleElement()
                .extracting(AppTag::votes).isEqualTo(20225);
    }

    @Test
    void emptyArrayTags_yieldsEmptyList() {
        // SteamSpy serializes "no tags" as an empty ARRAY, not an object
        assertThat(parser.parseTags("{\"appid\":5,\"name\":\"x\",\"tags\":[]}")).isEmpty();
    }

    @Test
    void missingTagsOrBlankOrGarbage_yieldsEmptyList() {
        assertThat(parser.parseTags("{\"appid\":5}")).isEmpty();
        assertThat(parser.parseTags("")).isEmpty();
        assertThat(parser.parseTags(null)).isEmpty();
        assertThat(parser.parseTags("not json")).isEmpty();
    }

    @Test
    void blankTagNamesAreSkipped_andNamesTruncatedTo255() {
        String longName = "T".repeat(300);
        String json = "{\"tags\":{\"\":10,\"" + longName + "\":7}}";

        List<AppTag> tags = parser.parseTags(json);

        assertThat(tags).singleElement().satisfies(t -> {
            assertThat(t.name()).hasSize(255);
            assertThat(t.votes()).isEqualTo(7);
        });
    }
}
