package SteamGame.recommend.domain.event;

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
class FunnelEventMigrationTest extends PostgresTestBase {

    @Autowired JdbcTemplate jdbc;

    private void insertImpression(String context) {
        jdbc.update("INSERT INTO recommendation_impression(client_id, appid, context) VALUES ('c', 1, ?)", context);
    }

    private void insertFeedback(String context) {
        jdbc.update("INSERT INTO recommendation_feedback(client_id, appid, context, signal) VALUES ('c', 1, ?, 'UP')", context);
    }

    @Test
    void impressionAcceptsTaste() {
        insertImpression("TASTE");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM recommendation_impression WHERE context='TASTE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void feedbackAcceptsTaste() {
        insertFeedback("TASTE");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM recommendation_feedback WHERE context='TASTE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void impressionStillRejectsUnknown() {
        assertThatThrownBy(() -> insertImpression("BOGUS")).isInstanceOf(DataIntegrityViolationException.class);
    }

    // 한 테스트 트랜잭션 안에서 제약 위반이 나면 이후 문장이 모두 막히므로 테이블마다 따로 검사한다
    @Test
    void feedbackStillRejectsUnknown() {
        assertThatThrownBy(() -> insertFeedback("BOGUS")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void funnelEventRejectsUnknownType() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO funnel_event(event_type) VALUES ('BOGUS')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void funnelEventRejectsUnknownChannel() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO funnel_event(event_type, channel) VALUES ('SHARE_CLICK', 'BOGUS')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void funnelEventDefaultsCreatedAt() {
        jdbc.update("INSERT INTO funnel_event(event_type) VALUES ('LANDING_VISIT')");
        assertThat(jdbc.queryForObject(
                "SELECT created_at FROM funnel_event WHERE event_type='LANDING_VISIT'", java.sql.Timestamp.class))
                .isNotNull();
    }
}
