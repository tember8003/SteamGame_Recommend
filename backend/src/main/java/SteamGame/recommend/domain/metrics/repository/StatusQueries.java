package SteamGame.recommend.domain.metrics.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

// 기간과 무관하게 지금 상태만 보는 조회, 대시보드 조회(DashboardQueries)와 표는 같지만 묻는 것이 다르다
@Repository
public class StatusQueries {

    private final JdbcTemplate jdbc;

    public StatusQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // client_id는 고르지 않는다, 운영 화면에 사람 식별자를 남기지 않음
    public record Event(Instant createdAt, String eventType, String ref, String channel,
                        String ledgerCode, String crimeCode, String genreCode) {
    }

    // 성공한 마이그레이션 중 버전이 있는 것의 가장 최근 값, 없으면 빈 값으로 둔다
    // 반복 마이그레이션(R__)은 version이 NULL이고 success가 true이면서 installed_rank가 가장 크므로 제외함
    public Optional<String> flywayVersion() {
        String version = jdbc.query(
                "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL"
                        + " ORDER BY installed_rank DESC LIMIT 1",
                rs -> rs.next() ? rs.getString("version") : null);
        return Optional.ofNullable(version);
    }

    // 최근 이벤트를 최신순으로 limit개 돌려준다, 같은 시각이면 id가 큰 쪽(나중에 들어온 쪽)을 먼저 둔다
    public List<Event> recentEvents(int limit) {
        return jdbc.query(
                "SELECT created_at, event_type, ref, channel, ledger_code, crime_code, genre_code FROM funnel_event"
                        + " ORDER BY created_at DESC, id DESC LIMIT ?",
                (rs, i) -> new Event(
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getString("event_type"),
                        rs.getString("ref"),
                        rs.getString("channel"),
                        rs.getString("ledger_code"),
                        rs.getString("crime_code"),
                        rs.getString("genre_code")),
                limit);
    }

    // 작업 종류 하나의 crawl_log 요약이다, 기록이 없으면 시각과 개수 칸은 null 로, 횟수는 0 으로 둔다
    // last* 는 시작 시각이 가장 늦은 기록(같으면 id 가 큰 쪽), lastFinishedRun* 은 그중 끝난 기록만 본 것이다
    // 성공은 끝났고 note 가 FAILED: 로 시작하지 않는 기록이다, previousSuccessProcessed 에는 그 직전 성공의 처리 수를 담는다
    // lastNote 는 원문 그대로라 응답에 싣기 전에 CollectionHealth.safeNote 로 걸러야 함
    public record JobSummary(String batchType, Instant lastStartedAt, Instant lastFinishedAt, Integer lastProcessed,
                             Integer lastFailed, String lastNote, Instant lastFinishedRunAt, boolean lastFinishedRunFailed,
                             Instant lastSuccessAt, Integer lastSuccessProcessed, Integer previousSuccessProcessed,
                             int runs7d, int failedRuns7d) {

        // 마지막 기록이 끝나지 않았으면 실행 중이다, 프로세스가 죽은 경우도 여기에 들어가므로 응답에서는 24시간 넘은 것을 멈춤으로 따로 나눔
        public boolean running() {
            return lastStartedAt != null && lastFinishedAt == null;
        }
    }

    // 작업 보고와 견줄 실제 DB 상태다, 시각은 기록이 없으면 null 로 둔다
    public record CollectionData(long games, long embeddingMissing, long tagVectorMissing, long reviewTextWrites,
                                 Instant lastFunnelEventAt, Instant lastImpressionAt) {
    }

    // 요청한 작업 종류마다 하나씩 요청 순서대로 돌려준다, 기록이 없는 종류도 빈 요약으로 채움
    // crawl_log 는 한 달에 수백 건이라 작업 종류마다 LATERAL 로 몇 번 훑어도 가볍다
    // 성공 둘은 창 함수로 한 번에 구한다, 카탈로그 저장 수 감소를 직전 성공과 견주기 위함
    public List<JobSummary> jobSummaries(List<String> batchTypes, Instant runsSince) {
        String sql = """
                SELECT t.batch_type,
                       l.started_at AS last_started_at, l.finished_at AS last_finished_at,
                       l.processed_count AS last_processed, l.failed_count AS last_failed, l.note AS last_note,
                       f.finished_at AS last_finished_run_at,
                       COALESCE(starts_with(f.note, 'FAILED:'), false) AS last_finished_run_failed,
                       s.success_at, s.success_processed, s.previous_success_processed,
                       w.runs_7d, w.failed_runs_7d
                FROM unnest(?::text[]) WITH ORDINALITY AS t(batch_type, ord)
                LEFT JOIN LATERAL (
                    SELECT started_at, finished_at, processed_count, failed_count, note FROM crawl_log c
                    WHERE c.batch_type = t.batch_type
                    ORDER BY started_at DESC, id DESC LIMIT 1
                ) l ON true
                LEFT JOIN LATERAL (
                    SELECT finished_at, note FROM crawl_log c
                    WHERE c.batch_type = t.batch_type AND finished_at IS NOT NULL
                    ORDER BY started_at DESC, id DESC LIMIT 1
                ) f ON true
                LEFT JOIN LATERAL (
                    SELECT MAX(finished_at) FILTER (WHERE rn = 1) AS success_at,
                           MAX(processed_count) FILTER (WHERE rn = 1) AS success_processed,
                           MAX(processed_count) FILTER (WHERE rn = 2) AS previous_success_processed
                    FROM (
                        SELECT finished_at, processed_count,
                               row_number() OVER (ORDER BY started_at DESC, id DESC) AS rn
                        FROM crawl_log c
                        WHERE c.batch_type = t.batch_type AND finished_at IS NOT NULL
                          AND (note IS NULL OR NOT starts_with(note, 'FAILED:'))
                    ) ok
                ) s ON true
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) AS runs_7d,
                           COUNT(*) FILTER (WHERE finished_at IS NOT NULL AND starts_with(note, 'FAILED:')) AS failed_runs_7d
                    FROM crawl_log c
                    WHERE c.batch_type = t.batch_type AND started_at >= ?
                ) w ON true
                ORDER BY t.ord
                """;
        return jdbc.query(sql,
                ps -> {
                    ps.setArray(1, ps.getConnection().createArrayOf("text", batchTypes.toArray()));
                    ps.setObject(2, utc(runsSince));
                },
                (rs, i) -> new JobSummary(
                        rs.getString("batch_type"),
                        instant(rs, "last_started_at"),
                        instant(rs, "last_finished_at"),
                        rs.getObject("last_processed", Integer.class),
                        rs.getObject("last_failed", Integer.class),
                        rs.getString("last_note"),
                        instant(rs, "last_finished_run_at"),
                        rs.getBoolean("last_finished_run_failed"),
                        instant(rs, "success_at"),
                        rs.getObject("success_processed", Integer.class),
                        rs.getObject("previous_success_processed", Integer.class),
                        rs.getInt("runs_7d"),
                        rs.getInt("failed_runs_7d")));
    }

    // games 는 한 번만 훑어 세 개수를 함께 센다, 4만여 건이라 매 조회 전체를 읽어도 가볍다
    // 벡터 칸은 IS NULL 만 보므로 벡터 값을 풀어 읽지 않음
    public CollectionData collectionData(Instant reviewWritesSince) {
        String sql = """
                SELECT g.games, g.embedding_missing, g.tag_vector_missing,
                       (SELECT COUNT(*) FROM reviews WHERE text_updated_at >= ?) AS review_text_writes,
                       (SELECT MAX(created_at) FROM funnel_event) AS last_funnel_event_at,
                       (SELECT MAX(created_at) FROM recommendation_impression) AS last_impression_at
                FROM (
                    SELECT COUNT(*) AS games,
                           COUNT(*) FILTER (WHERE embedding IS NULL) AS embedding_missing,
                           COUNT(*) FILTER (WHERE tag_embedding IS NULL
                               AND EXISTS (SELECT 1 FROM game_tags gt WHERE gt.game_id = games.id)) AS tag_vector_missing
                    FROM games
                ) g
                """;
        return jdbc.queryForObject(sql,
                (rs, i) -> new CollectionData(
                        rs.getLong("games"),
                        rs.getLong("embedding_missing"),
                        rs.getLong("tag_vector_missing"),
                        rs.getLong("review_text_writes"),
                        instant(rs, "last_funnel_event_at"),
                        instant(rs, "last_impression_at")),
                utc(reviewWritesSince));
    }

    // 기준 시각은 시간대가 붙은 값으로 넘긴다, 세션 시간대와 무관하게 같은 순간으로 비교하기 위함
    private static OffsetDateTime utc(Instant t) {
        return OffsetDateTime.ofInstant(t, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime t = rs.getObject(column, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }
}
