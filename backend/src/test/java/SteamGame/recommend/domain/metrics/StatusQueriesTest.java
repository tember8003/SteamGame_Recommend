package SteamGame.recommend.domain.metrics;

import SteamGame.recommend.domain.metrics.repository.DashboardQueries;
import SteamGame.recommend.domain.metrics.repository.StatusQueries;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// DashboardQueriesTest 와 애너테이션을 똑같이 둬 테스트 컨텍스트와 연결 풀을 함께 쓴다, 달라지면 컨텍스트가 하나 더 생겨 공유 테스트 DB의 연결 한도를 넘음
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DashboardQueries.class, StatusQueries.class})
class StatusQueriesTest extends PostgresTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired StatusQueries queries;

    // DB 연결 시간대를 UTC로 고정함, 실행 PC의 시간대가 한국이면 숨은 결함이 생길 수 있음
    @BeforeEach
    void fixSessionTimeZoneToUtc() {
        jdbc.execute("SET TIME ZONE 'UTC'");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM funnel_event");
        jdbc.update("DELETE FROM flyway_schema_history WHERE version = '999'");
    }

    private void event(String client, String type, String ref, String channel, String utc) {
        jdbc.update("INSERT INTO funnel_event (client_id, event_type, ref, channel, created_at) VALUES (?, ?, ?, ?, ?::timestamptz)",
                client, type, ref, channel, utc);
    }

    private void library(String client, String ledger, String crime, String genre, String utc) {
        jdbc.update("INSERT INTO funnel_event (client_id, event_type, ledger_code, crime_code, genre_code, created_at) "
                + "VALUES (?, 'LIBRARY_COMPLETE', ?, ?, ?, ?::timestamptz)", client, ledger, crime, genre, utc);
    }

    @Test
    void flywayVersion_returnsLatestSuccessfulVersion() {
        int nextRank = jdbc.queryForObject("SELECT MAX(installed_rank) + 1 FROM flyway_schema_history", Integer.class);
        jdbc.update("INSERT INTO flyway_schema_history "
                        + "(installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) "
                        + "VALUES (?, '999', '가짜', 'SQL', 'fake.sql', 0, 'test', now(), 0, true)",
                nextRank);

        assertThat(queries.flywayVersion()).contains("999");
    }

    // 반복 마이그레이션(R__)은 version이 NULL이라 가장 큰 installed_rank를 차지해도 건너뛰어야 한다
    @Test
    void flywayVersion_버전_없는_반복_마이그레이션은_건너뛴다() {
        String goodVersion = queries.flywayVersion().orElseThrow();
        int nextRank = jdbc.queryForObject("SELECT MAX(installed_rank) + 1 FROM flyway_schema_history", Integer.class);
        jdbc.update("INSERT INTO flyway_schema_history "
                        + "(installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) "
                        + "VALUES (?, NULL, '반복', 'SQL', 'repeat.sql', 0, 'test', now(), 0, true)",
                nextRank);

        assertThat(queries.flywayVersion()).contains(goodVersion);
    }

    // 실패한 마이그레이션이 설치 순서가 더 뒤라도 최신으로 보이면 안 된다
    @Test
    void flywayVersion_실패한_마이그레이션은_최신으로_보지_않는다() {
        String goodVersion = queries.flywayVersion().orElseThrow();
        int nextRank = jdbc.queryForObject("SELECT MAX(installed_rank) + 1 FROM flyway_schema_history", Integer.class);
        jdbc.update("INSERT INTO flyway_schema_history "
                        + "(installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) "
                        + "VALUES (?, '999', '가짜', 'SQL', 'fake.sql', 0, 'test', now(), 0, false)",
                nextRank);

        assertThat(queries.flywayVersion()).contains(goodVersion);
    }

    @Test
    void recentEvents_ordersNewestFirstAndRespectsLimit() {
        event("a", "LANDING_VISIT", null, null, "2026-11-01T00:00:00Z");
        event("a", "LANDING_VISIT", null, null, "2026-11-02T00:00:00Z");
        event("a", "LANDING_VISIT", null, null, "2026-11-03T00:00:00Z");

        List<StatusQueries.Event> events = queries.recentEvents(2);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).createdAt()).isEqualTo(Instant.parse("2026-11-03T00:00:00Z"));
        assertThat(events.get(1).createdAt()).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
    }

    // 시각이 같으면 나중에 들어온(id가 큰) 것이 먼저 나와야 한다
    @Test
    void recentEvents_sameTimestampOrdersByIdDescending() {
        event("a", "FEATURE_SELECT", "TASTE", null, "2026-11-01T00:00:00Z");
        event("b", "FEATURE_SELECT", "LIBRARY", null, "2026-11-01T00:00:00Z");

        List<StatusQueries.Event> events = queries.recentEvents(2);

        assertThat(events).extracting(StatusQueries.Event::ref).containsExactly("LIBRARY", "TASTE");
    }

    @Test
    void recentEvents_includesColumnsNeededForJudgmentAndNeverClientId() {
        library("a", "COLLECTOR", "INNOCENT", "ACTION_SHOOTER", "2026-11-01T00:00:00Z");

        StatusQueries.Event e = queries.recentEvents(10).get(0);

        assertThat(e).isEqualTo(new StatusQueries.Event(
                Instant.parse("2026-11-01T00:00:00Z"), "LIBRARY_COMPLETE", null, null,
                "COLLECTOR", "INNOCENT", "ACTION_SHOOTER"));
    }

    // client_id를 담을 자리가 레코드에 없어야 응답에 사람 식별자가 새지 않는다
    @Test
    void event_hasNoClientIdComponent() {
        assertThat(StatusQueries.Event.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("clientId", "client_id");
    }
}
