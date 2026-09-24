package SteamGame.recommend.domain.metrics;

import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.feedback.entity.FeedbackSignal;
import SteamGame.recommend.domain.feedback.entity.RecommendationFeedback;
import SteamGame.recommend.domain.feedback.repository.RecommendationFeedbackRepository;
import SteamGame.recommend.domain.impression.entity.RecommendationImpression;
import SteamGame.recommend.domain.impression.repository.RecommendationImpressionRepository;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 집계 조회를 실제 Postgres 로 검증한다, Docker 없는 환경에서는 돌지 않고 로컬이나 CI 에서만 통과로 본다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MetricsAggregationQueryTest extends PostgresTestBase {

    @Autowired RecommendationImpressionRepository impressionRepository;
    @Autowired RecommendationFeedbackRepository feedbackRepository;

    @Test
    void countByContext_groupsPerContext() {
        impressionRepository.saveAll(List.of(
                new RecommendationImpression("c1", null, 10L, FeedbackContext.INPUT, 1),
                new RecommendationImpression("c1", null, 11L, FeedbackContext.INPUT, 2),
                new RecommendationImpression("c1", null, 12L, FeedbackContext.MATCH, 1)));

        var rows = impressionRepository.countByContext();

        long input = rows.stream().filter(r -> r.getContext() == FeedbackContext.INPUT)
                .findFirst().orElseThrow().getCount();
        long match = rows.stream().filter(r -> r.getContext() == FeedbackContext.MATCH)
                .findFirst().orElseThrow().getCount();
        assertThat(input).isEqualTo(2L);
        assertThat(match).isEqualTo(1L);
    }

    @Test
    void topAppids_ordersByCountDescAndLimits() {
        impressionRepository.saveAll(List.of(
                new RecommendationImpression("c1", null, 10L, FeedbackContext.INPUT, 1),
                new RecommendationImpression("c1", null, 10L, FeedbackContext.INPUT, 2),
                new RecommendationImpression("c1", null, 20L, FeedbackContext.INPUT, 1)));

        var rows = impressionRepository.topAppids(PageRequest.of(0, 1));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAppid()).isEqualTo(10L);
        assertThat(rows.get(0).getCount()).isEqualTo(2L);
    }

    @Test
    void countByContextAndSignal_groupsPerContextAndSignal() {
        RecommendationFeedback up = new RecommendationFeedback();
        up.setClientId("c1");
        up.setAppid(10L);
        up.setContext(FeedbackContext.INPUT);
        up.setSignal(FeedbackSignal.UP);
        RecommendationFeedback down = new RecommendationFeedback();
        down.setClientId("c1");
        down.setAppid(11L);
        down.setContext(FeedbackContext.INPUT);
        down.setSignal(FeedbackSignal.DOWN);
        feedbackRepository.saveAll(List.of(up, down));

        var rows = feedbackRepository.countByContextAndSignal();

        long inputUp = rows.stream()
                .filter(r -> r.getContext() == FeedbackContext.INPUT && r.getSignal() == FeedbackSignal.UP)
                .findFirst().orElseThrow().getCount();
        assertThat(inputUp).isEqualTo(1L);
    }
}
