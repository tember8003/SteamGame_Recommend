package SteamGame.recommend.infra.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class VectorSamplerTest {

    @Test
    void poolSmallerThanNeed_returnsAll() {
        List<Integer> pool = List.of(1, 2);
        assertThat(VectorSampler.sample(pool, 4, new Random(1))).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void picksNeed_withoutDuplicates_fromPool() {
        List<Integer> pool = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<Integer> picked = VectorSampler.sample(pool, 4, new Random(42));

        assertThat(picked).hasSize(4);
        assertThat(picked).doesNotHaveDuplicates();
        assertThat(pool).containsAll(picked);
    }

    @Test
    void sameSeed_isDeterministic() {
        List<Integer> pool = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(VectorSampler.sample(pool, 4, new Random(7)))
                .isEqualTo(VectorSampler.sample(pool, 4, new Random(7)));
    }

    @Test
    void emptyOrNullPool_returnsEmpty() {
        assertThat(VectorSampler.sample(List.of(), 4, new Random(1))).isEmpty();
        assertThat(VectorSampler.sample(null, 4, new Random(1))).isEmpty();
    }
}
