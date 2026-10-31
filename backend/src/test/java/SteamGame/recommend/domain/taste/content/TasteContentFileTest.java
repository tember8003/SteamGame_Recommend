package SteamGame.recommend.domain.taste.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 taste-v1.json을 읽어 구조와 문구 규칙, 운영 태그 이름과의 일치를 확인한다
class TasteContentFileTest {

    // 설계 문서 부록의 축별 후보 태그. S 칸은 혼자 몰입하는 쪽 태그로 대신한다
    private static final Map<Character, Set<String>> AXIS_TAGS = Map.of(
            'E', Set.of("Exploration", "Open World", "Metroidvania", "Walking Simulator", "Survival", "Atmospheric", "Immersive Sim"),
            'G', Set.of("RPG", "Character Customization", "Loot", "Crafting", "Base Building", "Hack and Slash", "JRPG", "Party-Based RPG"),
            'N', Set.of("Story Rich", "Choices Matter", "Multiple Endings", "Visual Novel", "Lore-Rich", "Narrative", "Mystery", "Detective"),
            'M', Set.of("Strategy", "Management", "Automation", "Tactical", "Turn-Based Strategy", "Resource Management",
                    "City Builder", "Colony Sim", "Grand Strategy", "4X", "Deckbuilding", "Sandbox", "Programming"),
            'H', Set.of("Difficult", "Souls-like", "Roguelike", "Roguelite", "Precision Platformer", "Bullet Hell", "Competitive", "PvP"),
            'C', Set.of("Relaxing", "Cozy", "Cute", "Farming Sim", "Life Sim", "Wholesome"),
            'S', Set.of("Story Rich", "Atmospheric", "Immersive Sim"),
            'P', Set.of("Co-op", "Online Co-Op", "Local Co-Op", "Party Game", "Team-Based", "Massively Multiplayer", "MMORPG"));

    private static final Set<String> COMMON_TAGS =
            Set.of("Singleplayer", "Adventure", "Action", "Casual", "Indie", "Multiplayer", "Simulation");

    private static TasteContent load() {
        return new TasteContentLoader(new ObjectMapper()).content();
    }

    private static Set<String> prodTagNames() throws IOException {
        try (InputStream in = new ClassPathResource("taste/prod-tag-names.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        }
    }

    @Test
    void loader_readsAndValidatesRealFile() {
        TasteContentLoader loader = new TasteContentLoader(new ObjectMapper());
        assertThat(loader.content().version()).isEqualTo("v1");
        assertThat(loader.content().types()).hasSize(16);
        assertThat(loader.content().questions()).hasSize(12);
    }

    @Test
    void allWeightedTagsExistInProdCatalog() throws IOException {
        Set<String> prod = prodTagNames();
        assertThat(prod).hasSize(481);
        List<String> missing = new ArrayList<>();
        for (TasteType t : load().types()) {
            for (TagWeight w : t.tagWeights()) {
                if (!prod.contains(w.tag())) {
                    missing.add(t.code() + ":" + w.tag());
                }
            }
        }
        assertThat(missing).as("운영 태그에 없는 이름").isEmpty();
    }

    @Test
    void noCommonTagsInWeights() {
        List<String> found = new ArrayList<>();
        for (TasteType t : load().types()) {
            for (TagWeight w : t.tagWeights()) {
                if (COMMON_TAGS.contains(w.tag())) {
                    found.add(t.code() + ":" + w.tag());
                }
            }
        }
        assertThat(found).as("흔한 태그").isEmpty();
    }

    @Test
    void noForbiddenWording() throws IOException {
        try (InputStream in = new ClassPathResource("taste/taste-v1.json").getInputStream()) {
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(raw.toLowerCase()).doesNotContain("mbti");
            assertThat(raw).doesNotContain("·");
        }
    }

    @Test
    void everyTypeLeansOnItsOwnAxes() {
        List<String> weak = new ArrayList<>();
        for (TasteType t : load().types()) {
            Set<String> own = new HashSet<>();
            for (char letter : t.code().toCharArray()) {
                own.addAll(AXIS_TAGS.get(letter));
            }
            long hits = t.tagWeights().stream()
                    .sorted(Comparator.comparingDouble(TagWeight::weight).reversed())
                    .limit(3)
                    .filter(w -> own.contains(w.tag()))
                    .count();
            if (hits < 2) {
                weak.add(t.code());
            }
        }
        assertThat(weak).as("상위 3개 태그 중 자기 축 태그가 2개 미만인 유형").isEmpty();
    }
}
