package SteamGame.recommend.infra.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingTextBuilderTest {

    @Test
    void build_composesAllFields() {
        String doc = EmbeddingTextBuilder.build(
                "Hades", "Action, Indie", List.of("Roguelike", "Action"),
                "A rogue-like dungeon crawler", 1000);

        assertThat(doc).contains("Hades");
        assertThat(doc).contains("Genres: Action, Indie");
        assertThat(doc).contains("Tags: Roguelike, Action");
        assertThat(doc).contains("A rogue-like dungeon crawler");
    }

    @Test
    void build_truncatesDescription() {
        String longDesc = "x".repeat(50);
        String doc = EmbeddingTextBuilder.build("G", null, List.of(), longDesc, 10);

        // 이름 줄 + 잘린 설명(10자)만 남음
        assertThat(doc).isEqualTo("G\n" + "x".repeat(10));
    }

    @Test
    void build_skipsBlankFields() {
        String doc = EmbeddingTextBuilder.build("Only Name", null, List.of(), "  ", 1000);

        assertThat(doc).isEqualTo("Only Name");
        assertThat(doc).doesNotContain("Genres:");
        assertThat(doc).doesNotContain("Tags:");
    }

    @Test
    void build_allNull_returnsEmpty() {
        assertThat(EmbeddingTextBuilder.build(null, null, null, null, 1000)).isEmpty();
    }
}
