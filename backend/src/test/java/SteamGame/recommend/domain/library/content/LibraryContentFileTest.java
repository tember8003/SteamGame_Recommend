package SteamGame.recommend.domain.library.content;

import SteamGame.recommend.domain.library.analysis.CrimeCode;
import SteamGame.recommend.domain.library.analysis.LedgerCode;
import SteamGame.recommend.domain.library.analysis.StatCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 library-v1.json을 읽어 구조, 문구 규칙, 운영 태그 이름과의 일치를 확인한다
class LibraryContentFileTest {

    private static LibraryContent load() {
        return new LibraryContentLoader(new ObjectMapper()).content();
    }

    private static Set<String> prodTagNames() throws IOException {
        try (InputStream in = new ClassPathResource("taste/prod-tag-names.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        }
    }

    @Test
    void loader_readsAndValidatesRealFile() {
        LibraryContent c = load();
        assertThat(c.version()).isEqualTo("v1");
        assertThat(c.tagGroups().keySet()).containsExactlyInAnyOrderElementsOf(LibraryContentValidator.TAG_GROUP_KEYS);
    }

    @Test
    void everyVerdictCodeHasPhrases() {
        LibraryContent c = load();
        assertThat(LibraryContentValidator.requiredPhraseCodes()).hasSize(21);
        for (String code : LibraryContentValidator.requiredPhraseCodes()) {
            assertThat(c.phrases().get(code)).as(code).hasSizeBetween(2, 3);
        }
    }

    // 상수 이름은 저장 형식이라 문구 키와 어긋나면 옛 공유 링크가 깨진다
    @Test
    void everyStoredCodeEnumConstantHasPhrasesInContentFile() {
        LibraryContent c = load();
        List<String> expected = new ArrayList<>();
        for (LedgerCode code : LedgerCode.values()) {
            expected.add(code.name());
        }
        for (CrimeCode code : CrimeCode.values()) {
            expected.add(code.name());
            expected.add("SENTENCE_" + code.name());
        }
        for (StatCode code : StatCode.values()) {
            expected.add("STAT_" + code.name());
        }
        for (String key : expected) {
            assertThat(c.phrases().get(key)).as(key).isNotEmpty();
        }
        assertThat(expected).containsExactlyInAnyOrderElementsOf(LibraryContentValidator.requiredPhraseCodes());
    }

    @Test
    void allGroupTagsExistInProdCatalog() throws IOException {
        Set<String> prod = prodTagNames();
        assertThat(prod).hasSize(481);
        List<String> missing = new ArrayList<>();
        load().tagGroups().forEach((group, tags) -> tags.forEach(tag -> {
            if (!prod.contains(tag)) {
                missing.add(group + ":" + tag);
            }
        }));
        assertThat(missing).as("운영 태그에 없는 이름").isEmpty();
    }

    @Test
    void allGenreTagsExistInProdCatalog() throws IOException {
        Set<String> prod = prodTagNames();
        List<String> missing = new ArrayList<>();
        LibraryContent c = load();
        c.genres().forEach((genre, tags) -> tags.forEach(tag -> {
            if (!prod.contains(tag)) {
                missing.add(genre + ":" + tag);
            }
        }));
        c.genreExcludedTags().forEach(tag -> {
            if (!prod.contains(tag)) {
                missing.add("excluded:" + tag);
            }
        });
        c.genreConditionalExcludes().forEach(rule -> {
            rule.whenAnyTag().stream().filter(tag -> !prod.contains(tag)).forEach(tag -> missing.add("when:" + tag));
            rule.ignoreTags().stream().filter(tag -> !prod.contains(tag)).forEach(tag -> missing.add("ignore:" + tag));
        });
        assertThat(missing).as("운영 태그에 없는 이름").isEmpty();
    }

    // 여러 장르의 게임에 두루 붙는 태그와 레이싱이 아닌 게임에도 높게 붙는 Automobile Sim은 장르 판단에서 뺀다
    @Test
    void broadTagsAreExcludedFromGenres() {
        LibraryContent c = load();
        assertThat(c.genres().keySet()).containsExactlyElementsOf(LibraryContentValidator.GENRE_KEYS);
        assertThat(c.genreExcludedTags()).containsExactlyInAnyOrder(
                "Open World", "Adventure", "Exploration", "Story Rich", "Choices Matter", "Automobile Sim");
        assertThat(c.genreRules()).isEqualTo(new GenreRules(10, 0.3, 0.1, 0.15));
        assertThat(c.picks()).isEqualTo(new PickRules(10, 5, 2, 50, 70, 3, 10, 365, 3, 60, 120, 90, 3));
    }

    // 배틀로얄과 익스트랙션 슈터는 Survival 표가 많아도 생존 게임이 아니므로 이 두 태그가 있으면 Survival을 장르 판단에서 뺀다
    @Test
    void battleRoyaleIgnoresSurvival() {
        assertThat(load().genreConditionalExcludes()).containsExactly(
                new GenreConditionalExclude(List.of("Battle Royale", "Extraction Shooter"), List.of("Survival")));
    }

    @Test
    void nearUniversalTagsAreInNoGroup() {
        Set<String> universal = Set.of("Action", "Adventure", "Casual", "Indie", "Singleplayer", "Simulation", "Multiplayer");
        LibraryContent c = load();
        assertThat(c.excludedTags()).containsAll(universal);
        List<String> found = new ArrayList<>();
        c.tagGroups().forEach((group, tags) -> tags.forEach(tag -> {
            if (universal.contains(tag)) {
                found.add(group + ":" + tag);
            }
        }));
        assertThat(found).isEmpty();
    }

    @Test
    void hasTwelveGroupsIncludingRpgHorrorSportsCompetitive() {
        assertThat(load().tagGroups().keySet()).hasSize(12)
                .contains("RPG", "HORROR", "SPORTS", "COMPETITIVE", "EXPLORATION", "STRATEGY", "COOP");
        assertThat(load().tagGroups().get("RPG")).contains("RPG");
        assertThat(load().excludedTags()).doesNotContain("RPG");
    }

    @Test
    void groupLabelsExistForEveryGroup() {
        LibraryContent c = load();
        assertThat(c.groupLabels().keySet()).containsExactlyInAnyOrderElementsOf(LibraryContentValidator.TAG_GROUP_KEYS);
    }

    @Test
    void genreLoyalistUsesGroupPlaceholders() {
        load().phrases().get("GENRE_LOYALIST").forEach(p -> {
            assertThat(p.params()).doesNotContain("topTag", "topTagRatioPct");
            assertThat(p.params()).contains("topGroup");
        });
    }

    @Test
    void noForbiddenWording() throws IOException {
        try (InputStream in = new ClassPathResource("library/library-v1.json").getInputStream()) {
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(raw.toLowerCase()).doesNotContain("mbti");
            assertThat(raw).doesNotContain("·");
        }
    }

    @Test
    void phrasesCarryNoLiteralDigits() {
        // 숫자는 자리표시자로만 들어간다
        List<String> found = new ArrayList<>();
        load().phrases().forEach((code, list) -> list.forEach(p -> {
            if (p.text().ko().matches(".*\\d.*") || p.text().en().matches(".*\\d.*")) {
                found.add(code + ": " + p.text().ko());
            }
        }));
        assertThat(found).isEmpty();
    }
}
