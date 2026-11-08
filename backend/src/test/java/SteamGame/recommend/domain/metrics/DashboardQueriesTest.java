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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// StatusQueriesTest 와 애너테이션을 똑같이 둬 테스트 컨텍스트와 연결 풀을 함께 쓴다, 달라지면 컨텍스트가 하나 더 생겨 공유 테스트 DB의 연결 한도를 넘음
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DashboardQueries.class, StatusQueries.class})
class DashboardQueriesTest extends PostgresTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired DashboardQueries queries;

    // 한국 시간 11-02 0시부터 11-09 0시 전까지
    private static final DashboardRange.Bounds WEEK = new DashboardRange.Bounds(
            OffsetDateTime.parse("2026-11-01T15:00:00Z"), OffsetDateTime.parse("2026-11-08T15:00:00Z"),
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8));

    // DB 연결 시간대를 UTC로 고정한다, 실행 PC의 시간대가 한국이면 UTC 날짜로 자르는 결함이 숨기 때문임
    @BeforeEach
    void fixSessionTimeZoneToUtc() {
        jdbc.execute("SET TIME ZONE 'UTC'");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM funnel_event");
        jdbc.update("DELETE FROM recommendation_feedback");
        jdbc.update("DELETE FROM recommendation_impression");
    }

    private void event(String client, String type, String ref, String channel, String utc) {
        jdbc.update("INSERT INTO funnel_event (client_id, event_type, ref, channel, created_at) VALUES (?, ?, ?, ?, ?::timestamptz)",
                client, type, ref, channel, utc);
    }

    private void library(String client, String ledger, String crime, String genre, String utc) {
        jdbc.update("INSERT INTO funnel_event (client_id, event_type, ledger_code, crime_code, genre_code, created_at) "
                + "VALUES (?, 'LIBRARY_COMPLETE', ?, ?, ?, ?::timestamptz)", client, ledger, crime, genre, utc);
    }

    // UTC 14:59 는 한국 23:59, UTC 15:00 은 다음 날 한국 00:00 이다
    @Test
    void trendSplitsDaysAtKoreanMidnight() {
        event("a", "TEST_COMPLETE", "EMCS", null, "2026-11-03T14:59:00Z");
        event("b", "TEST_COMPLETE", "EMCS", null, "2026-11-03T15:00:00Z");

        List<DashboardQueries.Daily> days = queries.trend(WEEK);

        assertThat(days).extracting(DashboardQueries.Daily::date, DashboardQueries.Daily::testCompletes)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 11, 3), 1L),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 11, 4), 1L));
    }

    @Test
    void eventsOutsideBoundsAreExcluded() {
        event("a", "LANDING_VISIT", null, null, "2026-11-01T14:59:59Z");
        event("b", "LANDING_VISIT", null, null, "2026-11-08T15:00:00Z");
        event("c", "LANDING_VISIT", null, null, "2026-11-01T15:00:00Z");

        assertThat(queries.funnel(WEEK).landing()).isEqualTo(1);
    }

    @Test
    void funnelCountsDistinctPeopleAndSkipsNullClient() {
        event("a", "LANDING_VISIT", null, null, "2026-11-03T01:00:00Z");
        event("a", "LANDING_VISIT", null, null, "2026-11-04T01:00:00Z");
        event(null, "LANDING_VISIT", null, null, "2026-11-04T01:00:00Z");
        // b 는 방문 없이 바로 테스트를 끝냈다, 앞 단계 조건 없이 센다
        event("b", "TEST_COMPLETE", "EMCS", null, "2026-11-04T01:00:00Z");
        event("b", "TEST_COMPLETE", "GMHS", null, "2026-11-04T02:00:00Z");
        event("a", "FEATURE_SELECT", "TASTE", null, "2026-11-03T01:00:00Z");
        event("a", "FEATURE_SELECT", "LIBRARY", null, "2026-11-03T01:00:00Z");
        event("a", "SHARE_CLICK", "EMCS", "LINK", "2026-11-03T01:00:00Z");
        event("c", "SHARE_CLICK", "LIBRARY", "IMAGE", "2026-11-03T01:00:00Z");
        library("c", "COLLECTOR", "INNOCENT", null, "2026-11-03T01:00:00Z");

        DashboardQueries.Funnel f = queries.funnel(WEEK);

        assertThat(f.landing()).isEqualTo(1);
        assertThat(f.tasteSelect()).isEqualTo(1);
        assertThat(f.testComplete()).isEqualTo(1);
        assertThat(f.tasteShare()).isEqualTo(1);
        assertThat(f.librarySelect()).isEqualTo(1);
        assertThat(f.libraryComplete()).isEqualTo(1);
        assertThat(f.libraryShare()).isEqualTo(1);
        assertThat(f.testStart()).isZero();
    }

    @Test
    void trendVisitorsAreDistinctPerDayAndCountsAreEvents() {
        event("a", "LANDING_VISIT", null, null, "2026-11-03T01:00:00Z");
        event("a", "LANDING_VISIT", null, null, "2026-11-03T02:00:00Z");
        event("b", "LANDING_VISIT", null, null, "2026-11-03T03:00:00Z");
        event("a", "TEST_COMPLETE", "EMCS", null, "2026-11-03T04:00:00Z");
        event("a", "TEST_COMPLETE", "EMCS", null, "2026-11-03T05:00:00Z");
        library(null, "STARTER", "INNOCENT", null, "2026-11-03T06:00:00Z");

        DashboardQueries.Daily d = queries.trend(WEEK).get(0);

        assertThat(d.visitors()).isEqualTo(2);
        assertThat(d.testCompletes()).isEqualTo(2);
        assertThat(d.libraryCompletes()).isEqualTo(1);
    }

    @Test
    void distributionsAndRecordedSince() {
        event("a", "FEATURE_SELECT", "TASTE", null, "2026-11-03T01:00:00Z");
        event("b", "FEATURE_SELECT", "TASTE", null, "2026-11-03T01:00:00Z");
        event("a", "TEST_COMPLETE", "EMCS", null, "2026-11-03T01:00:00Z");
        // 판정 칼럼이 생기기 전의 분석은 판정이 비어 있다
        jdbc.update("INSERT INTO funnel_event (client_id, event_type, created_at) VALUES ('z', 'LIBRARY_COMPLETE', '2026-10-01T01:00:00Z')");
        // 배포 직후 7일 기간에는 판정이 빈 옛 분석이 섞인다, 기록 부족 칸으로 세면 안 된다
        jdbc.update("INSERT INTO funnel_event (client_id, event_type, created_at) VALUES ('y', 'LIBRARY_COMPLETE', '2026-11-03T02:00:00Z')");
        library("a", "COLLECTOR", "PILE_OF_SHAME", "ACTION_SHOOTER", "2026-11-03T01:00:00Z");
        library("b", "COLLECTOR", "INNOCENT", null, "2026-11-04T16:00:00Z");

        assertThat(queries.countByRef("FEATURE_SELECT", WEEK)).containsEntry("TASTE", 2L);
        assertThat(queries.countByRef("TEST_COMPLETE", WEEK)).containsEntry("EMCS", 1L);
        assertThat(queries.countLibraryCode("ledger_code", WEEK)).containsEntry("COLLECTOR", 2L);
        assertThat(queries.countLibraryCode("crime_code", WEEK)).containsEntry("PILE_OF_SHAME", 1L).containsEntry("INNOCENT", 1L);
        assertThat(queries.countLibraryCode("genre_code", WEEK)).containsOnlyKeys("ACTION_SHOOTER");
        assertThat(queries.countLibraryInsufficient(WEEK)).isEqualTo(1);
        assertThat(queries.libraryRecordedSince()).contains(LocalDate.of(2026, 11, 3));
        assertThat(queries.firstEventDate()).contains(LocalDate.of(2026, 10, 1));
    }

    // all 은 "처음부터" 이므로 funnel_event 가 늦게 생겼어도 추천 노출이 더 이르면 그 날짜를 돌려줘야 한다
    @Test
    void firstEventDateUsesEarliestAcrossAllThreeTables_impressionEarliest() {
        jdbc.update("INSERT INTO recommendation_impression (client_id, appid, context, rank_position, created_at) "
                + "VALUES ('a', 10, 'INPUT', 1, '2026-08-01T01:00:00Z')");
        event("a", "LANDING_VISIT", null, null, "2026-11-03T01:00:00Z");

        assertThat(queries.firstEventDate()).contains(LocalDate.of(2026, 8, 1));
    }

    @Test
    void firstEventDateUsesEarliestAcrossAllThreeTables_feedbackEarliest() {
        jdbc.update("INSERT INTO recommendation_feedback (client_id, appid, context, signal, created_at) "
                + "VALUES ('a', 10, 'INPUT', 'UP', '2026-08-15T01:00:00Z')");
        event("a", "LANDING_VISIT", null, null, "2026-11-03T01:00:00Z");

        assertThat(queries.firstEventDate()).contains(LocalDate.of(2026, 8, 15));
    }

    // UTC 7월 31일 16시는 한국 시간 8월 1일 새벽이다
    @Test
    void firstEventDateIsKstDate() {
        jdbc.update("INSERT INTO recommendation_impression (client_id, appid, context, rank_position, created_at) "
                + "VALUES ('a', 10, 'INPUT', 1, '2026-07-31T16:00:00Z')");

        assertThat(queries.firstEventDate()).contains(LocalDate.of(2026, 8, 1));
    }

    @Test
    void firstEventDateIsEmptyWhenAllThreeTablesAreEmpty() {
        assertThat(queries.firstEventDate()).isEmpty();
    }

    @Test
    void sharesAreSplitByTargetTypeAndChannel() {
        event("a", "SHARE_CLICK", "EMCS", "LINK", "2026-11-03T01:00:00Z");
        event("a", "SHARE_CLICK", "LIBRARY", "IMAGE", "2026-11-03T01:00:00Z");
        event("b", "SHARE_INFLOW", "GMHS", null, "2026-11-03T01:00:00Z");

        assertThat(queries.shares(WEEK)).containsExactlyInAnyOrder(
                new DashboardQueries.ShareCount("TASTE", "SHARE_CLICK", "LINK", 1),
                new DashboardQueries.ShareCount("LIBRARY", "SHARE_CLICK", "IMAGE", 1),
                new DashboardQueries.ShareCount("TASTE", "SHARE_INFLOW", null, 1));
    }

    @Test
    void recommendationCountsAreCutByBounds() {
        jdbc.update("INSERT INTO recommendation_impression (client_id, appid, context, rank_position, created_at) VALUES ('a', 10, 'INPUT', 1, '2026-11-03T01:00:00Z')");
        jdbc.update("INSERT INTO recommendation_impression (client_id, appid, context, rank_position, created_at) VALUES ('a', 10, 'INPUT', 1, '2026-10-01T01:00:00Z')");
        jdbc.update("INSERT INTO recommendation_feedback (client_id, appid, context, signal, created_at) VALUES ('a', 10, 'INPUT', 'UP', '2026-11-03T01:00:00Z')");

        assertThat(queries.impressionsByContext(WEEK)).containsEntry("INPUT", 1L);
        assertThat(queries.feedback(WEEK)).containsExactly(new DashboardQueries.FeedbackCount("INPUT", "UP", 1));
        assertThat(queries.topImpressions(WEEK, 20)).containsExactly(new DashboardQueries.AppidCount(10, 1));
    }
}
