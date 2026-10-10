package SteamGame.recommend.domain.recommendation.similar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameNormalizerTest {

    @Test
    void stripsEditionSuffixAndSymbols() {
        assertThat(NameNormalizer.normalize("Ori and the Blind Forest: Definitive Edition"))
                .isEqualTo("ori and the blind forest");
        assertThat(NameNormalizer.normalize("The Witcher 3: Wild Hunt - Game of the Year Edition"))
                .isEqualTo("the witcher 3 wild hunt");
        assertThat(NameNormalizer.normalize("DARK SOULS™: REMASTERED")).isEqualTo("dark souls");
        assertThat(NameNormalizer.normalize("Death Stranding Director's Cut")).isEqualTo("death stranding");
    }

    @Test
    void keepsNumbersSoSequelsStayDistinct() {
        assertThat(NameNormalizer.normalize("Portal")).isNotEqualTo(NameNormalizer.normalize("Portal 2"));
    }

    @Test
    void sameGameDifferentEditionsMatch() {
        assertThat(NameNormalizer.normalize("Ori and the Blind Forest"))
                .isEqualTo(NameNormalizer.normalize("Ori and the Blind Forest: Definitive Edition"));
    }

    @Test
    void nullIsEmpty() {
        assertThat(NameNormalizer.normalize(null)).isEmpty();
    }
}
