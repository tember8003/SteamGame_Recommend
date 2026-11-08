package SteamGame.recommend.domain.metrics.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
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
}
