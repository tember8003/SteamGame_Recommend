package SteamGame.recommend.domain.library.steam;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SteamIdResolverTest {

    private static final String ID = "76561198000000000";

    private final List<String> asked = new ArrayList<>();
    private final SteamIdResolver resolver = new SteamIdResolver(name -> {
        asked.add(name);
        return name.equals("Gabe") ? Optional.of("76561198000000001") : Optional.empty();
    });

    @Test
    void plainIdAndWhitespace() {
        assertThat(resolver.resolve(ID)).isEqualTo(ID);
        assertThat(resolver.resolve("  " + ID + "\n")).isEqualTo(ID);
        assertThat(asked).isEmpty();
    }

    @Test
    void profileUrl() {
        assertThat(resolver.resolve("https://steamcommunity.com/profiles/" + ID + "/")).isEqualTo(ID);
        assertThat(resolver.resolve("http://steamcommunity.com/profiles/" + ID)).isEqualTo(ID);
        assertThat(asked).isEmpty();
    }

    @Test
    void vanityUrlAndName() {
        assertThat(resolver.resolve("https://steamcommunity.com/id/Gabe/")).isEqualTo("76561198000000001");
        assertThat(resolver.resolve("Gabe")).isEqualTo("76561198000000001");
        assertThat(asked).containsExactly("Gabe", "Gabe");
    }

    @Test
    void urlsWithoutSchemeQueryOrFragment() {
        assertThat(resolver.resolve("steamcommunity.com/id/Gabe")).isEqualTo("76561198000000001");
        assertThat(resolver.resolve("www.steamcommunity.com/profiles/" + ID)).isEqualTo(ID);
        assertThat(resolver.resolve("https://steamcommunity.com/id/Gabe?l=english")).isEqualTo("76561198000000001");
        assertThat(resolver.resolve("https://steamcommunity.com/id/Gabe/?l=english#x")).isEqualTo("76561198000000001");
        assertThat(resolver.resolve("https://steamcommunity.com/profiles/" + ID + "#x")).isEqualTo(ID);
        assertThat(resolver.resolve("steamcommunity.com/profiles/" + ID + "/?l=korean")).isEqualTo(ID);
        assertThat(asked).containsOnly("Gabe");
    }

    @Test
    void vanityNotFound() {
        assertThatThrownBy(() -> resolver.resolve("nobodyhere"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getReason()).isEqualTo("STEAM_PROFILE_NOT_FOUND");
                });
    }

    @Test
    void invalidInputs() {
        String[] bad = {"abc def", "", "   ", "7656119800000000", "a".repeat(33), "x",
                "12345678901234567", "https://steamcommunity.com/profiles/123/",
                "https://example.com/id/Gabe", "example.com/id/Gabe",
                "evilsteamcommunity.com/id/Gabe"};
        for (String input : bad) {
            assertThatThrownBy(() -> resolver.resolve(input))
                    .as(input)
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.getReason()).isEqualTo("INVALID_STEAM_ID");
                    });
        }
        assertThat(asked).isEmpty();
    }

    @Test
    void nullInputIsInvalid() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getReason()).isEqualTo("INVALID_STEAM_ID"));
    }
}
