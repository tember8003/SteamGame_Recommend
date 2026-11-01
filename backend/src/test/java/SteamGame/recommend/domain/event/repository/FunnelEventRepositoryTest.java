package SteamGame.recommend.domain.event.repository;

import SteamGame.recommend.domain.event.entity.FunnelEvent;
import SteamGame.recommend.domain.event.entity.FunnelEventType;
import SteamGame.recommend.domain.event.entity.ShareChannel;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// FunnelEventMigrationTest와 같은 설정이라 스프링 컨텍스트를 새로 만들지 않고 나눠 쓴다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FunnelEventRepositoryTest extends PostgresTestBase {

    @Autowired FunnelEventRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void savesEveryEventTypeAndReadsBackWithJdbc() {
        for (FunnelEventType type : FunnelEventType.values()) {
            repository.save(new FunnelEvent("rt-" + type.name(), type, "GMHS", null));
        }
        repository.flush();

        for (FunnelEventType type : FunnelEventType.values()) {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT client_id, event_type, ref, channel, created_at FROM funnel_event WHERE client_id = ?",
                    "rt-" + type.name());
            assertThat(row.get("event_type")).isEqualTo(type.name());
            assertThat(row.get("ref")).isEqualTo("GMHS");
            assertThat(row.get("channel")).isNull();
            assertThat(row.get("created_at")).isNotNull();
        }
    }

    @Test
    void savesBothChannelsAndReadsBackWithJdbc() {
        for (ShareChannel channel : ShareChannel.values()) {
            repository.save(new FunnelEvent("rt-ch-" + channel.name(), FunnelEventType.SHARE_CLICK, "ENCP", channel));
        }
        repository.flush();

        List<String> channels = jdbc.queryForList(
                "SELECT channel FROM funnel_event WHERE client_id LIKE 'rt-ch-%' ORDER BY channel", String.class);
        assertThat(channels).containsExactly("IMAGE", "LINK");
    }

    @Test
    void savesNullClientIdAndRef() {
        repository.save(new FunnelEvent(null, FunnelEventType.LANDING_VISIT, null, null));
        repository.flush();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT client_id, ref FROM funnel_event WHERE event_type = 'LANDING_VISIT' AND client_id IS NULL");
        assertThat(row.get("client_id")).isNull();
        assertThat(row.get("ref")).isNull();
    }
}
