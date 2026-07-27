package SteamGame.recommend.domain.feedback.repository;

import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.feedback.entity.FeedbackSignal;
import SteamGame.recommend.domain.feedback.entity.RecommendationFeedback;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecommendationFeedbackRepositoryTest extends PostgresTestBase {

    @Autowired RecommendationFeedbackRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void save_persistsRow_withGeneratedIdTimestamp_andEnumsAsText() {
        RecommendationFeedback f = new RecommendationFeedback();
        f.setClientId("client-1");
        f.setSteamId("76561198000000000");
        f.setAppid(570L);
        f.setContext(FeedbackContext.INPUT);
        f.setSignal(FeedbackSignal.UP);
        f.setRankPosition(0);

        RecommendationFeedback saved = repository.save(f);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT signal FROM recommendation_feedback WHERE id=?", String.class, saved.getId()))
                .isEqualTo("UP");
        assertThat(jdbc.queryForObject("SELECT context FROM recommendation_feedback WHERE id=?", String.class, saved.getId()))
                .isEqualTo("INPUT");
        assertThat(jdbc.queryForObject("SELECT steam_id FROM recommendation_feedback WHERE id=?", String.class, saved.getId()))
                .isEqualTo("76561198000000000");
        assertThat(jdbc.queryForObject("SELECT rank_position FROM recommendation_feedback WHERE id=?", Integer.class, saved.getId()))
                .isEqualTo(0);
    }

    @Test
    void checkConstraint_rejectsInvalidSignal() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO recommendation_feedback (client_id, appid, context, signal) VALUES (?,?,?,?)",
                "c", 570L, "INPUT", "LOL"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checkConstraint_rejectsInvalidContext() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO recommendation_feedback (client_id, appid, context, signal) VALUES (?,?,?,?)",
                "c", 570L, "NOPE", "UP"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
