package SteamGame.recommend.domain.event;

import SteamGame.recommend.domain.event.entity.FunnelEvent;
import SteamGame.recommend.domain.event.entity.FunnelEventType;
import SteamGame.recommend.domain.event.repository.FunnelEventRepository;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// V10 판정 칼럼과 CHECK 제약을 실제 Postgres 로 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FunnelEventLibraryCodesTest extends PostgresTestBase {

    @Autowired FunnelEventRepository repository;
    @Autowired JdbcTemplate jdbc;

    // @DataJpaTest는 테스트마다 트랜잭션을 걸고 끝나면 되돌리므로 별도 정리가 필요 없다
    // CHECK 위반 시험에서 정리문을 같은 트랜잭션에 넣으면 Postgres가 트랜잭션을 중단 상태로 두어 정리문도 실패한다(FunnelEventMigrationTest와 같은 이유)

    @Test
    void libraryCompleteStoresThreeCodes() {
        repository.saveAndFlush(FunnelEvent.libraryComplete("c1", "COLLECTOR", "PILE_OF_SHAME", "ACTION_SHOOTER"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT event_type, ledger_code, crime_code, genre_code FROM funnel_event");
        assertThat(row).containsEntry("event_type", "LIBRARY_COMPLETE")
                .containsEntry("ledger_code", "COLLECTOR")
                .containsEntry("crime_code", "PILE_OF_SHAME")
                .containsEntry("genre_code", "ACTION_SHOOTER");
    }

    @Test
    void genreMayBeNullWhenInsufficient() {
        repository.saveAndFlush(FunnelEvent.libraryComplete("c1", "STARTER", "INNOCENT", null));

        assertThat(jdbc.queryForObject("SELECT genre_code FROM funnel_event", String.class)).isNull();
    }

    @Test
    void otherEventsKeepCodesEmpty() {
        repository.saveAndFlush(new FunnelEvent("c1", FunnelEventType.TEST_START, null, null));

        Map<String, Object> row = jdbc.queryForMap("SELECT ledger_code, crime_code, genre_code FROM funnel_event");
        assertThat(row.values()).containsOnlyNulls();
    }

    // 다른 이벤트에 판정 값이 들어가면 DB 가 거부한다
    @Test
    void checkConstraintRejectsCodesOnOtherEvents() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO funnel_event (client_id, event_type, ledger_code) VALUES ('c1', 'TEST_START', 'COLLECTOR')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
