package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.SteamSpyAllEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SteamSpyParserTest {

    private final SteamSpyParser parser = new SteamSpyParser(new ObjectMapper());

    private String fixture() throws Exception {
        var res = new ClassPathResource("fixtures/steamspy_all_page.json");
        return FileCopyUtils.copyToString(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8));
    }

    @Test
    void parseAllPage_parsesEntriesWithReviewCounts() throws Exception {
        List<SteamSpyAllEntry> entries = parser.parseAllPage(fixture());

        assertThat(entries).hasSize(2);
        SteamSpyAllEntry dota = entries.stream()
                .filter(e -> e.getAppid() == 570L).findFirst().orElseThrow();
        assertThat(dota.getName()).isEqualTo("Dota 2");
        assertThat(dota.getDeveloper()).isEqualTo("Valve");
        assertThat(dota.getPositive()).isEqualTo(1200000);
        assertThat(dota.getNegative()).isEqualTo(250000);
        assertThat(dota.getOwners()).contains("100,000,000");
    }

    @Test
    void parseAllPage_emptyObject_returnsEmptyList() throws Exception {
        assertThat(parser.parseAllPage("{}")).isEmpty();
    }
}
