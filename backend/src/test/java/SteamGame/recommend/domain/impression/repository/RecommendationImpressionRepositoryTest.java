package SteamGame.recommend.domain.impression.repository;

import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.impression.entity.RecommendationImpression;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecommendationImpressionRepositoryTest extends PostgresTestBase {

    @Autowired RecommendationImpressionRepository repo;

    @Test
    void saveAll_persistsRowsWithMappingAndDefaults() {
        RecommendationImpression a = new RecommendationImpression("cid", null, 10L, FeedbackContext.MATCH, 1);
        RecommendationImpression b = new RecommendationImpression("cid", "steam1", 20L, FeedbackContext.PROFILE, 2);

        repo.saveAll(List.of(a, b));
        List<RecommendationImpression> rows = repo.findAll();

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getId()).isNotNull();
            assertThat(r.getCreatedAt()).isNotNull();   // @CreationTimestamp 가 채움
        });
        assertThat(rows).extracting(RecommendationImpression::getAppid).containsExactlyInAnyOrder(10L, 20L);
        assertThat(rows).extracting(RecommendationImpression::getContext)
                .containsExactlyInAnyOrder(FeedbackContext.MATCH, FeedbackContext.PROFILE);
    }
}
