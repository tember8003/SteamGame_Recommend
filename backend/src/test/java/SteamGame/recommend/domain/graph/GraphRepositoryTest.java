package SteamGame.recommend.domain.graph;

import SteamGame.recommend.domain.graph.dto.GraphNeighbor;
import SteamGame.recommend.domain.graph.dto.GraphNode;
import SteamGame.recommend.infra.embedding.EmbeddingRepository;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GraphRepositoryTest extends PostgresTestBase {

    @Autowired JdbcTemplate jdbc;
    GraphRepository repo;
    EmbeddingRepository embRepo;

    @BeforeEach
    void setUp() {
        repo = new GraphRepository(jdbc);
        embRepo = new EmbeddingRepository(jdbc);
    }

    private float[] vec(double... head) {
        float[] v = new float[768];
        for (int i = 0; i < head.length; i++) v[i] = (float) head[i];
        return v;
    }

    private void game(long appid, String name, float[] emb) {
        jdbc.update("INSERT INTO games (appid, name, image_url, genres) VALUES (?, ?, ?, ?)",
                appid, name, "img" + appid, "Action");
        if (emb != null) embRepo.updateEmbedding(appid, emb);
    }

    @Test
    void search_filtersByNameAndEmbedding() {
        game(400L, "Counter-Strike 2", vec(1.0));
        game(401L, "Counter Source", vec(0.9, 0.1));
        game(402L, "NoEmbed Counter", null);   // 임베딩 없음 → 제외
        game(403L, "Totally Other", vec(0.0, 1.0));

        List<GraphNode> hits = repo.searchByName("counter", 10);

        assertThat(hits).extracting(GraphNode::appid).containsExactlyInAnyOrder(400L, 401L);
    }

    @Test
    void neighbors_nearestFirst_excludesSeed() {
        game(410L, "Seed", vec(1.0));
        game(411L, "Near", vec(0.9, 0.1));
        game(412L, "Far", vec(0.0, 1.0));

        List<GraphNeighbor> n = repo.findNeighbors(410L, 5);

        assertThat(n).extracting(GraphNeighbor::appid).containsExactly(411L, 412L);
        assertThat(n.get(0).similarity()).isGreaterThan(n.get(1).similarity());
    }

    @Test
    void neighbors_emptyWhenSeedUnembedded() {
        game(420L, "NoEmb", null);
        game(421L, "Other", vec(1.0));
        assertThat(repo.findNeighbors(420L, 5)).isEmpty();
    }

    @Test
    void search_ordersByReviewCount() {
        game(430L, "Order Few", vec(1.0));
        game(431L, "Order Many", vec(0.9, 0.1));
        jdbc.update("UPDATE games SET review_count = 60 WHERE appid = 430");
        jdbc.update("UPDATE games SET review_count = 90000 WHERE appid = 431");

        List<GraphNode> hits = repo.searchByName("order", 10);

        assertThat(hits).extracting(GraphNode::appid).containsExactly(431L, 430L);
    }
}
