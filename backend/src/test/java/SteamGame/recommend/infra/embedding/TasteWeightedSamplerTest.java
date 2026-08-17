package SteamGame.recommend.infra.embedding;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class TasteWeightedSamplerTest {

    private final float[] taste = {1f, 0f};

    @Test
    void cosine_orthogonalIsZero_identicalIsOne_oppositeIsMinusOne() {
        assertThat(TasteWeightedSampler.cosine(new float[]{1f, 0f}, new float[]{0f, 1f})).isEqualTo(0.0);
        assertThat(TasteWeightedSampler.cosine(new float[]{1f, 0f}, new float[]{1f, 0f})).isEqualTo(1.0);
        assertThat(TasteWeightedSampler.cosine(new float[]{1f, 0f}, new float[]{-1f, 0f})).isEqualTo(-1.0);
    }

    @Test
    void cosine_nullOrZeroNormIsZero() {
        assertThat(TasteWeightedSampler.cosine(null, new float[]{1f, 0f})).isEqualTo(0.0);
        assertThat(TasteWeightedSampler.cosine(new float[]{1f, 0f}, null)).isEqualTo(0.0);
        assertThat(TasteWeightedSampler.cosine(new float[]{0f, 0f}, new float[]{1f, 0f})).isEqualTo(0.0);
    }

    @Test
    void sample_tinyTau_picksTopByCosineDescending() {
        Map<String, float[]> vecs = new HashMap<>();
        vecs.put("A", new float[]{1f, 0f});           // cos 1.0
        vecs.put("B", new float[]{0.8f, 0.6f});       // cos 0.8
        vecs.put("C", new float[]{0.5f, 0.8660254f}); // cos 0.5
        vecs.put("D", new float[]{0.2f, 0.9797959f}); // cos 0.2
        // "N" 은 맵에 없음 → 벡터 null → cos 0

        List<String> items = List.of("C", "N", "B", "A", "D");

        List<String> picked = TasteWeightedSampler.sample(
                items, vecs::get, taste, 3, 1e-9, new Random(1));

        assertThat(picked).containsExactly("A", "B", "C");
    }

    @Test
    void sample_unvectoredCandidateIsNeutral_notExcluded() {
        Map<String, float[]> vecs = new HashMap<>();
        vecs.put("X", new float[]{-1f, 0f});      // cos -1.0
        vecs.put("Z", new float[]{-0.6f, -0.8f}); // cos -0.6
        // "N" 없음 → cos 0, 음의 코사인 후보들보다 큼

        List<String> items = List.of("X", "N", "Z");

        List<String> picked = TasteWeightedSampler.sample(
                items, vecs::get, taste, 1, 1e-9, new Random(1));

        assertThat(picked).containsExactly("N");
    }

    @Test
    void sample_nonPositiveTau_clampsToGreedy_noNaN() {
        Map<String, float[]> vecs = new HashMap<>();
        vecs.put("A", new float[]{1f, 0f});     // cos 1.0
        vecs.put("B", new float[]{0.5f, 0.8660254f}); // cos 0.5
        vecs.put("C", new float[]{-1f, 0f});    // cos -1.0

        List<String> picked = TasteWeightedSampler.sample(
                List.of("C", "A", "B"), vecs::get, taste, 2, 0.0, new Random(1));

        assertThat(picked).containsExactly("A", "B");
    }

    @Test
    void sample_poolSmallerThanNeed_returnsAllInInputOrder() {
        Map<String, float[]> vecs = new HashMap<>();
        vecs.put("A", new float[]{1f, 0f});
        vecs.put("B", new float[]{0f, 1f});

        List<String> picked = TasteWeightedSampler.sample(
                List.of("A", "B"), vecs::get, taste, 5, 0.25, new Random(1));

        assertThat(picked).containsExactly("A", "B");
    }
}
