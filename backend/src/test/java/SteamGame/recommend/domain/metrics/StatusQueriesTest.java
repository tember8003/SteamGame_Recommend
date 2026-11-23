package SteamGame.recommend.domain.metrics;

import SteamGame.recommend.domain.metrics.repository.DashboardQueries;
import SteamGame.recommend.domain.metrics.repository.StatusQueries;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
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
        jdbc.update("DELETE FROM crawl_log");
        jdbc.update("DELETE FROM reviews WHERE appid BETWEEN 990000 AND 990099");
        jdbc.update("DELETE FROM games WHERE appid BETWEEN 990000 AND 990099");
        jdbc.update("DELETE FROM tags WHERE name LIKE 'status-test-%'");
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

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final List<String> TYPES = List.of("steam_catalog", "detail_rolling", "tag_embedding");

    private void crawl(String type, String startedUtc, String finishedUtc, int processed, int failed, String note) {
        jdbc.update("INSERT INTO crawl_log (batch_type, started_at, finished_at, processed_count, failed_count, note)"
                        + " VALUES (?, ?::timestamptz, ?::timestamptz, ?, ?, ?)",
                type, startedUtc, finishedUtc, processed, failed, note);
    }

    private StatusQueries.JobSummary summary(String type) {
        return queries.jobSummaries(TYPES, NOW.minus(Duration.ofDays(7))).stream()
                .filter(s -> s.batchType().equals(type)).findFirst().orElseThrow();
    }

    // 게임 하나를 넣는다, 두 벡터 칸을 채울지 고르고 tagged 면 game_tags 를 하나 단다
    private void game(long appid, boolean withEmbedding, boolean withTagVector, boolean tagged) {
        jdbc.update("INSERT INTO games (appid, name) VALUES (?, ?)", appid, "g" + appid);
        if (withEmbedding) {
            jdbc.update("UPDATE games SET embedding = array_fill(0.1::real, ARRAY[768])::vector WHERE appid = ?", appid);
        }
        if (withTagVector) {
            jdbc.update("UPDATE games SET tag_embedding = array_fill(0.1::real, ARRAY[320])::vector WHERE appid = ?", appid);
        }
        if (tagged) {
            String tag = "status-test-" + appid;
            jdbc.update("INSERT INTO tags (name) VALUES (?)", tag);
            jdbc.update("INSERT INTO game_tags (game_id, tag_id) SELECT g.id, t.id FROM games g, tags t"
                    + " WHERE g.appid = ? AND t.name = ?", appid, tag);
        }
    }

    private void review(long appid, String textUpdatedUtc) {
        jdbc.update("INSERT INTO reviews (appid, total_reviews, text_updated_at) VALUES (?, 0, ?::timestamptz)",
                appid, textUpdatedUtc);
    }

    // 기록이 없는 작업도 요청한 순서대로 빈 요약으로 돌려줘야 화면이 기록 없음을 보일 수 있다
    @Test
    void jobSummaries_returnsOnePerRequestedTypeInOrderEvenWithoutRecords() {
        crawl("detail_rolling", "2026-11-20T06:00:00Z", "2026-11-20T06:10:00Z", 5, 0, "withText=5 noText=0 malformed=0 failed=0");

        List<StatusQueries.JobSummary> all = queries.jobSummaries(TYPES, NOW.minus(Duration.ofDays(7)));

        assertThat(all).extracting(StatusQueries.JobSummary::batchType).containsExactlyElementsOf(TYPES);
        StatusQueries.JobSummary empty = all.get(0);
        assertThat(empty.lastStartedAt()).isNull();
        assertThat(empty.lastProcessed()).isNull();
        assertThat(empty.lastSuccessAt()).isNull();
        assertThat(empty.running()).isFalse();
        assertThat(empty.lastFinishedRunFailed()).isFalse();
        assertThat(empty.runs7d()).isZero();
        assertThat(empty.failedRuns7d()).isZero();
        assertThat(all.get(1).lastProcessed()).isEqualTo(5);
    }

    // 마지막 실행은 시작 시각이 가장 늦은 기록이고 같으면 id 가 큰 쪽이다
    @Test
    void jobSummaries_lastRunIsLatestStartedWithIdTiebreak() {
        crawl("steam_catalog", "2026-11-19T05:30:00Z", "2026-11-19T06:00:00Z", 100, 1, "first");
        crawl("steam_catalog", "2026-11-20T05:30:00Z", "2026-11-20T06:00:00Z", 200, 2, "second");
        crawl("steam_catalog", "2026-11-20T05:30:00Z", "2026-11-20T06:05:00Z", 300, 3, "third");

        StatusQueries.JobSummary s = summary("steam_catalog");

        assertThat(s.lastStartedAt()).isEqualTo(Instant.parse("2026-11-20T05:30:00Z"));
        assertThat(s.lastFinishedAt()).isEqualTo(Instant.parse("2026-11-20T06:05:00Z"));
        assertThat(s.lastProcessed()).isEqualTo(300);
        assertThat(s.lastFailed()).isEqualTo(3);
        assertThat(s.lastNote()).isEqualTo("third");
        assertThat(s.running()).isFalse();
        assertThat(s.lastSuccessProcessed()).isEqualTo(300);
        assertThat(s.previousSuccessProcessed()).isEqualTo(200);
    }

    // 끝나지 않은 마지막 실행은 실행 중이고, 실패 여부는 그 앞의 끝난 실행으로 본다
    @Test
    void jobSummaries_runningLastRunLooksAtPreviousFinishedRunForFailure() {
        crawl("detail_rolling", "2026-11-20T00:00:00Z", "2026-11-20T00:05:00Z", 0, 0,
                "FAILED: review api gone withText=0 noText=0 malformed=0 failed=0");
        crawl("detail_rolling", "2026-11-20T06:00:00Z", null, 0, 0, null);

        StatusQueries.JobSummary s = summary("detail_rolling");

        assertThat(s.running()).isTrue();
        assertThat(s.lastFinishedAt()).isNull();
        assertThat(s.lastFinishedRunAt()).isEqualTo(Instant.parse("2026-11-20T00:05:00Z"));
        assertThat(s.lastFinishedRunFailed()).isTrue();
    }

    // 성공은 끝났고 FAILED: 로 시작하지 않는 기록만이다, 실패와 실행 중 기록은 마지막 성공을 덮지 못함
    @Test
    void jobSummaries_successSkipsFailedAndUnfinishedRuns() {
        crawl("steam_catalog", "2026-11-18T05:30:00Z", "2026-11-18T06:00:00Z", 39958, 0, "pages=141");
        crawl("steam_catalog", "2026-11-19T05:30:00Z", "2026-11-19T05:31:00Z", 0, 0, "FAILED: timeout");
        crawl("steam_catalog", "2026-11-20T05:30:00Z", null, 0, 0, null);

        StatusQueries.JobSummary s = summary("steam_catalog");

        assertThat(s.lastSuccessAt()).isEqualTo(Instant.parse("2026-11-18T06:00:00Z"));
        assertThat(s.lastSuccessProcessed()).isEqualTo(39958);
        assertThat(s.previousSuccessProcessed()).isNull();
        assertThat(s.lastFinishedRunFailed()).isTrue();
        assertThat(s.running()).isTrue();
    }

    // note 가 없는 끝난 기록도 성공으로 친다
    @Test
    void jobSummaries_finishedRunWithoutNoteIsSuccess() {
        crawl("tag_embedding", "2026-11-20T00:00:00Z", "2026-11-20T04:00:00Z", 44656, 0, null);

        StatusQueries.JobSummary s = summary("tag_embedding");

        assertThat(s.lastFinishedRunFailed()).isFalse();
        assertThat(s.lastSuccessAt()).isEqualTo(Instant.parse("2026-11-20T04:00:00Z"));
    }

    // 7일 집계는 시작 시각이 기준 시각 이후인 실행만 세고, 실패는 그중 끝났고 FAILED: 로 시작하는 것만 셈
    @Test
    void jobSummaries_countsRunsAndFailuresWithinSevenDays() {
        crawl("detail_rolling", "2026-11-13T11:59:59Z", "2026-11-13T12:10:00Z", 1, 0, "FAILED: old");
        crawl("detail_rolling", "2026-11-13T12:00:00Z", "2026-11-13T12:10:00Z", 1, 0, "withText=1");
        crawl("detail_rolling", "2026-11-18T12:00:00Z", "2026-11-18T12:10:00Z", 0, 0, "FAILED: x");
        crawl("detail_rolling", "2026-11-20T06:00:00Z", null, 0, 0, null);

        StatusQueries.JobSummary s = summary("detail_rolling");

        assertThat(s.runs7d()).isEqualTo(3);
        assertThat(s.failedRuns7d()).isEqualTo(1);
    }

    @Test
    void collectionData_countsGamesMissingVectorsAndRecentTextWrites() {
        StatusQueries.CollectionData before = queries.collectionData(NOW.minus(Duration.ofHours(24)));
        game(990001, true, true, true);    // 다 채워짐
        game(990002, false, false, true);  // 설명문 벡터 없음, 태그가 있는데 GNN 벡터 없음
        game(990003, false, false, false); // 태그가 없어 GNN 벡터 없음으로 세지 않음
        review(990001, "2026-11-20T11:00:00Z");
        review(990002, "2026-11-19T12:00:00Z"); // 경계 시각은 포함함
        review(990003, "2026-11-19T11:59:59Z");
        event("a", "LANDING_VISIT", null, null, "2026-11-20T10:00:00Z");
        event("a", "LANDING_VISIT", null, null, "2026-11-20T11:00:00Z");
        jdbc.update("INSERT INTO recommendation_impression (client_id, appid, context, created_at)"
                + " VALUES ('a', 1, 'INPUT', '2026-11-20T09:00:00Z'::timestamptz)");

        StatusQueries.CollectionData d = queries.collectionData(NOW.minus(Duration.ofHours(24)));

        // 공유 테스트 DB에 다른 클래스가 남긴 게임이 있을 수 있어 넣기 전 값과의 차이로 본다
        assertThat(d.games() - before.games()).isEqualTo(3);
        assertThat(d.embeddingMissing() - before.embeddingMissing()).isEqualTo(2);
        assertThat(d.tagVectorMissing() - before.tagVectorMissing()).isEqualTo(1);
        assertThat(d.reviewTextWrites() - before.reviewTextWrites()).isEqualTo(2);
        assertThat(d.lastFunnelEventAt()).isEqualTo(Instant.parse("2026-11-20T11:00:00Z"));
        assertThat(d.lastImpressionAt()).isEqualTo(Instant.parse("2026-11-20T09:00:00Z"));
    }

    @Test
    void collectionData_emptyTablesGiveZerosAndNullTimes() {
        StatusQueries.CollectionData d = queries.collectionData(NOW.minus(Duration.ofHours(24)));

        assertThat(d.reviewTextWrites()).isZero();
        assertThat(d.lastFunnelEventAt()).isNull();
        assertThat(d.lastImpressionAt()).isNull();
    }
}
