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
    void findNeighborAppids_returnsNearestFirst_excludesSeedAndUnembedded() {
        game(350L, "Seed");
        game(351L, "Near");
        game(352L, "Far");
        game(353L, "NoEmb");
        repo.updateEmbedding(350L, vec(1.0, 0.0));
        repo.updateEmbedding(351L, vec(0.9, 0.1));    // seed 와 가까움
        repo.updateEmbedding(352L, vec(-1.0, 0.0));   // seed 와 반대라 멈
        // 353 은 임베딩 없음 → 결과에서 빠짐

        List<Long> neighbors = repo.findNeighborAppids(350L, 10);

        assertThat(neighbors).containsExactly(351L, 352L);   // 가까운 순, seed 350 제외, 353 제외
    }

    @Test
    void findNeighborAppids_respectsLimit_andEmptyWhenSeedUnembeddedOrKNonPositive() {
        game(360L, "Seed");
        game(361L, "A");
        game(362L, "B");
        game(363L, "NoEmbSeed");
        repo.updateEmbedding(360L, vec(1.0, 0.0));
        repo.updateEmbedding(361L, vec(0.9, 0.1));
        repo.updateEmbedding(362L, vec(0.8, 0.2));

        assertThat(repo.findNeighborAppids(360L, 1)).hasSize(1);      // LIMIT 적용
        assertThat(repo.findNeighborAppids(363L, 10)).isEmpty();      // seed 임베딩 없음
        assertThat(repo.findNeighborAppids(360L, 0)).isEmpty();       // k<=0 방어
    }

    @Test
    void findEmbeddingTexts_returnsLiteralsForEmbedded_skipsUnembedded() {
        game(370L, "A");
        game(371L, "B");
        game(372L, "NoEmb");
        repo.updateEmbedding(370L, vec(1.0, 2.0));
        repo.updateEmbedding(371L, vec(3.0, 4.0));
        // 372 는 임베딩 없음 → 맵에서 빠짐

        Map<Long, String> texts = repo.findEmbeddingTextsByAppids(List.of(370L, 371L, 372L));

        assertThat(texts).containsOnlyKeys(370L, 371L);
        // 배치로 읽은 리터럴을 그대로 이웃 조회에 넣으면 시드로 읽는 경로와 결과가 같아야 함
        assertThat(repo.findNeighborAppids(texts.get(370L), 370L, 10))
                .isEqualTo(repo.findNeighborAppids(370L, 10));
    }

    @Test
    void findEmbeddingTexts_emptyInput_returnsEmptyMap() {
        assertThat(repo.findEmbeddingTextsByAppids(List.of())).isEmpty();
    }

    @Test
    void findNeighborAppids_withProvidedVector_returnsNeighbors_guardsNullAndNonPositiveK() {
        game(380L, "Seed");
        game(381L, "A");
        repo.updateEmbedding(380L, vec(1.0, 0.0));
        repo.updateEmbedding(381L, vec(0.9, 0.1));
        String seedVec = repo.findEmbeddingTextsByAppids(List.of(380L)).get(380L);

        assertThat(repo.findNeighborAppids(seedVec, 380L, 10)).containsExactly(381L); // seed 제외, 이웃 반환
        assertThat(repo.findNeighborAppids(null, 380L, 10)).isEmpty();                // 벡터 null 방어
        assertThat(repo.findNeighborAppids(seedVec, 380L, 0)).isEmpty();              // k<=0 방어
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
