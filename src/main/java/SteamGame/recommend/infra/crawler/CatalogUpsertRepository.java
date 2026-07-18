package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.GameSeed;
import SteamGame.recommend.infra.crawler.dto.ReviewSeed;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CatalogUpsertRepository {

    private final JdbcTemplate jdbc;

    public CatalogUpsertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String UPSERT_GAME = """
        INSERT INTO games (appid, name, developer, publisher, owners_estimate, review_count)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (appid) DO UPDATE SET
            name            = EXCLUDED.name,
            developer       = EXCLUDED.developer,
            publisher       = EXCLUDED.publisher,
            owners_estimate = EXCLUDED.owners_estimate,
            review_count    = EXCLUDED.review_count
        """;

    private static final String UPSERT_REVIEW = """
        INSERT INTO reviews (appid, total_reviews, positive_percentage)
        VALUES (?, ?, ?)
        ON CONFLICT (appid) DO UPDATE SET
            total_reviews       = EXCLUDED.total_reviews,
            positive_percentage = EXCLUDED.positive_percentage
        """;

    public int upsertGames(List<GameSeed> seeds) {
        int[][] result = jdbc.batchUpdate(UPSERT_GAME, seeds, seeds.size(), (ps, s) -> {
            ps.setLong(1, s.appid());
            ps.setString(2, s.name());
            ps.setString(3, s.developer());
            ps.setString(4, s.publisher());
            if (s.ownersEstimate() != null) ps.setLong(5, s.ownersEstimate());
            else ps.setNull(5, java.sql.Types.BIGINT);
            ps.setInt(6, s.reviewCount());
        });
        return countAffected(result);
    }

    public int upsertReviews(List<ReviewSeed> seeds) {
        int[][] result = jdbc.batchUpdate(UPSERT_REVIEW, seeds, seeds.size(), (ps, s) -> {
            ps.setLong(1, s.appid());
            ps.setInt(2, s.totalReviews());
            ps.setBigDecimal(3, s.positivePercentage());
        });
        return countAffected(result);
    }

    private int countAffected(int[][] result) {
        int total = 0;
        for (int[] batch : result) {
            for (int r : batch) {
                if (r > 0) total += r;
            }
        }
        return total;
    }
}
