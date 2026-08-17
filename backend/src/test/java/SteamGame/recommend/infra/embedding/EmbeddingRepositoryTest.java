package SteamGame.recommend.infra.embedding;

import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmbeddingRepositoryTest extends PostgresTestBase {

    @Autowired JdbcTemplate jdbc;
    EmbeddingRepository repo;

    @BeforeEach
    void setUp() {
        repo = new EmbeddingRepository(jdbc);
    }

    private void game(long appid, String name) {
        jdbc.update("INSERT INTO games (appid, name) VALUES (?, ?)", appid, name);
    }

    private float[] vec(double... head) {
        float[] v = new float[768];
        for (int i = 0; i < head.length; i++) v[i] = (float) head[i];
        return v;
    }

    @Test
    void updateThenFind_roundTripsEmbedding() {
        game(300L, "G");
        repo.updateEmbedding(300L, vec(1.0, 0.5));

        float[] back = repo.findEmbeddingByAppid(300L);

        assertThat(back).isNotNull();
        assertThat(back).hasSize(768);
        assertThat(back[0]).isEqualTo(1.0f);
        assertThat(back[1]).isEqualTo(0.5f);
    }

    @Test
    void findEmbedding_returnsNull_whenAbsent() {
        game(301L, "NoEmb");
        assertThat(repo.findEmbeddingByAppid(301L)).isNull();
    }

    @Test
    void findMeanEmbedding_componentWiseAverage_ignoringUnembedded() {
        game(310L, "A");
        game(311L, "B");
        game(312L, "NoEmb");
        repo.updateEmbedding(310L, vec(0.0, 2.0));
        repo.updateEmbedding(311L, vec(2.0, 4.0));
        // 312 는 임베딩 없음 → 평균에서 제외

        float[] mean = repo.findMeanEmbeddingByAppids(List.of(310L, 311L, 312L));

        assertThat(mean).isNotNull();
        assertThat(mean[0]).isEqualTo(1.0f);   // (0+2)/2
        assertThat(mean[1]).isEqualTo(3.0f);   // (2+4)/2
    }

    @Test
    void findMeanEmbedding_returnsNull_whenNoneEmbeddedOrEmptyInput() {
        game(320L, "X");
        assertThat(repo.findMeanEmbeddingByAppids(List.of(320L))).isNull();
        assertThat(repo.findMeanEmbeddingByAppids(List.of())).isNull();
    }

    @Test
    void findEmbeddings_returnsVectors_skipsUnembedded() {
        game(340L, "A");
        game(341L, "B");
        game(342L, "NoEmb");
        repo.updateEmbedding(340L, vec(1.0, 2.0));
        repo.updateEmbedding(341L, vec(3.0, 4.0));
        // 342 는 임베딩 없음 → 맵에서 빠짐

        Map<Long, float[]> map = repo.findEmbeddingsByAppids(List.of(340L, 341L, 342L));

        assertThat(map).containsOnlyKeys(340L, 341L);
        assertThat(map.get(340L)[0]).isEqualTo(1.0f);
        assertThat(map.get(340L)[1]).isEqualTo(2.0f);
        assertThat(map.get(341L)[0]).isEqualTo(3.0f);
    }

    @Test
    void findEmbeddings_emptyInput_returnsEmptyMap() {
        assertThat(repo.findEmbeddingsByAppids(List.of())).isEmpty();
    }

    @Test
    void selectNextToEmbed_picksUnembedded_skipsEmbedded() {
        game(330L, "Needs");
        game(331L, "Has");
        repo.updateEmbedding(331L, vec(1.0));

        List<Long> next = repo.selectNextToEmbed(50);

        assertThat(next).contains(330L);
        assertThat(next).doesNotContain(331L);
    }
}
