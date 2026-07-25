package SteamGame.recommend.infra.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class VectorLiteralsTest {

    @Test
    void toPgVector_usesDotDecimal_evenUnderCommaLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY); // 콤마 소수점 로케일
            String lit = VectorLiterals.toPgVector(new float[]{0.5f, 1.25f});
            assertThat(lit).isEqualTo("[0.5,1.25]");
            assertThat(lit).doesNotContain("0,5");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void parse_roundTrips() {
        float[] v = {0.1f, -0.2f, 3.0f};
        float[] back = VectorLiterals.parse(VectorLiterals.toPgVector(v));
        assertThat(back).containsExactly(v, org.assertj.core.data.Offset.offset(1e-6f));
    }

    @Test
    void parse_emptyLiteral_returnsEmptyArray() {
        assertThat(VectorLiterals.parse("[]")).isEmpty();
        assertThat(VectorLiterals.parse(null)).isNull();
    }

    @Test
    void mean_componentWiseAverage() {
        float[] m = VectorLiterals.mean(List.of(
                new float[]{0f, 2f, 4f},
                new float[]{2f, 4f, 6f}));
        assertThat(m).containsExactly(new float[]{1f, 3f, 5f}, org.assertj.core.data.Offset.offset(1e-6f));
    }

    @Test
    void mean_emptyOrNull_returnsNull() {
        assertThat(VectorLiterals.mean(List.of())).isNull();
        assertThat(VectorLiterals.mean(null)).isNull();
    }
}
