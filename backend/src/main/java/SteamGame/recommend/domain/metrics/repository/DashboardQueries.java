package SteamGame.recommend.domain.metrics.repository;

import SteamGame.recommend.domain.metrics.DashboardRange;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// 대시보드 영역별 집계 SQL, 기간은 from 포함 to 미포함이고 날짜는 한국 시간으로 자른다
// 맵을 채우는 람다는 RowCallbackHandler 로 형변환한다, 안 하면 ResultSetExtractor 로 해석되어 행마다 호출되지 않을 수 있음
// 사람 수는 COUNT(DISTINCT client_id) 라 client_id 가 null 인 이벤트는 사람 수에서 빠진다
@Repository
public class DashboardQueries {

    private static final String KST_DATE = "(created_at AT TIME ZONE 'Asia/Seoul')::date";
    // 퍼널과 추이가 보는 이벤트 종류, WHERE 에 넣어 (event_type, created_at) 인덱스를 쓰게 한다
    private static final String FUNNEL_TYPES = "event_type IN ('LANDING_VISIT', 'FEATURE_SELECT', 'TEST_START',"
            + " 'TEST_COMPLETE', 'SHARE_CLICK', 'LIBRARY_COMPLETE')";
    private static final Set<String> LIBRARY_COLUMNS = Set.of("ledger_code", "crime_code", "genre_code");

    private final JdbcTemplate jdbc;

    public DashboardQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Funnel(long landing, long tasteSelect, long testStart, long testComplete, long tasteShare,
                         long librarySelect, long libraryComplete, long libraryShare) {
    }

    public record Daily(LocalDate date, long visitors, long testCompletes, long libraryCompletes) {
    }

    public record ShareCount(String target, String eventType, String channel, long count) {
    }

    public record FeedbackCount(String context, String signal, long count) {
    }

    public record AppidCount(long appid, long count) {
    }

    private static Timestamp ts(java.time.OffsetDateTime t) {
        return Timestamp.from(t.toInstant());
    }

    // all 기간의 시작점이라 세 표(funnel_event, recommendation_impression, recommendation_feedback)를 모두 본다
    // funnel_event 는 11월 리뉴얼부터 쌓였지만 추천 노출/피드백은 8월부터 있어, funnel_event 만 보면 그보다 이른 기록이 잘린다
    // MIN 을 created_at 그대로 구한 뒤 날짜로 바꾼다, 추천 두 표는 created_at 인덱스로 바로 끝남
    // funnel_event 는 created_at 으로 시작하는 인덱스가 없어 전체를 읽지만 표가 작아 그대로 둠
    public Optional<LocalDate> firstEventDate() {
        LocalDate d = jdbc.queryForObject("SELECT (MIN(t) AT TIME ZONE 'Asia/Seoul')::date FROM ("
                + "SELECT MIN(created_at) AS t FROM funnel_event"
                + " UNION ALL SELECT MIN(created_at) FROM recommendation_impression"
                + " UNION ALL SELECT MIN(created_at) FROM recommendation_feedback"
                + ") m", LocalDate.class);
        return Optional.ofNullable(d);
    }

    // 단계마다 앞 단계 조건 없이 그 행동을 한 사람 수를 센다
    public Funnel funnel(DashboardRange.Bounds b) {
        return jdbc.queryForObject("""
                SELECT
                  COUNT(DISTINCT client_id) FILTER (WHERE event_type = 'LANDING_VISIT') AS landing,
                  COUNT(DISTINCT client_id) FILTER (WHERE event_type = 'FEATURE_SELECT' AND ref = 'TASTE') AS taste_select,
                  COUNT(DISTINCT client_id) FILTER (WHERE event_type = 'TEST_START') AS test_start,
                  COUNT(DISTINCT client_id) FILTER (WHERE event_type = 'TEST_COMPLETE') AS test_complete,
                  COUNT(DISTINCT client_id) FILTER (WHERE event_type = 'SHARE_CLICK' AND ref <> 'LIBRARY') AS taste_share,
                  COUNT(DISTINCT client_id) FILTER (WHERE event_type = 'FEATURE_SELECT' AND ref = 'LIBRARY') AS library_select,
                  COUNT(DISTINCT client_id) FILTER (WHERE event_type = 'LIBRARY_COMPLETE') AS library_complete,
                  COUNT(DISTINCT client_id) FILTER (WHERE event_type = 'SHARE_CLICK' AND ref = 'LIBRARY') AS library_share
                FROM funnel_event
                WHERE %s AND created_at >= ? AND created_at < ?
                """.formatted(FUNNEL_TYPES), (rs, i) -> new Funnel(rs.getLong("landing"), rs.getLong("taste_select"), rs.getLong("test_start"),
                rs.getLong("test_complete"), rs.getLong("taste_share"), rs.getLong("library_select"),
                rs.getLong("library_complete"), rs.getLong("library_share")), ts(b.from()), ts(b.to()));
    }

    // 이벤트가 있는 날만 돌려준다, 빈 날 채우기는 서비스가 한다
    public List<Daily> trend(DashboardRange.Bounds b) {
        return jdbc.query("SELECT " + KST_DATE + " AS d,"
                        + " COUNT(DISTINCT client_id) FILTER (WHERE event_type = 'LANDING_VISIT') AS visitors,"
                        + " COUNT(*) FILTER (WHERE event_type = 'TEST_COMPLETE') AS tests,"
                        + " COUNT(*) FILTER (WHERE event_type = 'LIBRARY_COMPLETE') AS libs"
                        + " FROM funnel_event WHERE " + FUNNEL_TYPES + " AND created_at >= ? AND created_at < ?"
                        + " GROUP BY d ORDER BY d",
                (rs, i) -> new Daily(rs.getObject("d", LocalDate.class), rs.getLong("visitors"), rs.getLong("tests"),
                        rs.getLong("libs")), ts(b.from()), ts(b.to()));
    }

    public Map<String, Long> countByRef(String eventType, DashboardRange.Bounds b) {
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.query("SELECT ref, COUNT(*) AS c FROM funnel_event WHERE event_type = ? AND ref IS NOT NULL"
                        + " AND created_at >= ? AND created_at < ? GROUP BY ref",
                (RowCallbackHandler) rs -> { out.put(rs.getString("ref"), rs.getLong("c")); }, eventType, ts(b.from()), ts(b.to()));
        return out;
    }

    // column 은 판정 칼럼 세 개 중 하나만 받는다, 문자열로 SQL 에 들어가므로 정해 둔 이름만 허용한다
    public Map<String, Long> countLibraryCode(String column, DashboardRange.Bounds b) {
        if (!LIBRARY_COLUMNS.contains(column)) {
            throw new IllegalArgumentException("unknown library column: " + column);
        }
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.query("SELECT " + column + " AS code, COUNT(*) AS c FROM funnel_event WHERE event_type = 'LIBRARY_COMPLETE'"
                        + " AND " + column + " IS NOT NULL AND created_at >= ? AND created_at < ? GROUP BY " + column,
                (RowCallbackHandler) rs -> { out.put(rs.getString("code"), rs.getLong("c")); }, ts(b.from()), ts(b.to()));
        return out;
    }

    // 판정은 남았지만 장르 기록이 부족했던 분석 수
    public long countLibraryInsufficient(DashboardRange.Bounds b) {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM funnel_event WHERE event_type = 'LIBRARY_COMPLETE'"
                + " AND ledger_code IS NOT NULL AND genre_code IS NULL AND created_at >= ? AND created_at < ?",
                Long.class, ts(b.from()), ts(b.to()));
        return c == null ? 0 : c;
    }

    // 기간과 상관없이 판정 기록이 처음 남은 한국 시간 날짜
    public Optional<LocalDate> libraryRecordedSince() {
        LocalDate d = jdbc.queryForObject("SELECT MIN(" + KST_DATE + ") FROM funnel_event"
                + " WHERE event_type = 'LIBRARY_COMPLETE' AND ledger_code IS NOT NULL", LocalDate.class);
        return Optional.ofNullable(d);
    }

    // 공유 대상은 ref 가 LIBRARY 면 라이브러리, 나머지(유형 코드)는 취향이다
    public List<ShareCount> shares(DashboardRange.Bounds b) {
        return jdbc.query("SELECT CASE WHEN ref = 'LIBRARY' THEN 'LIBRARY' ELSE 'TASTE' END AS target,"
                        + " event_type, channel, COUNT(*) AS c FROM funnel_event"
                        + " WHERE event_type IN ('SHARE_CLICK', 'SHARE_INFLOW') AND created_at >= ? AND created_at < ?"
                        + " GROUP BY target, event_type, channel",
                (rs, i) -> new ShareCount(rs.getString("target"), rs.getString("event_type"), rs.getString("channel"),
                        rs.getLong("c")), ts(b.from()), ts(b.to()));
    }

    public Map<String, Long> impressionsByContext(DashboardRange.Bounds b) {
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.query("SELECT context, COUNT(*) AS c FROM recommendation_impression"
                        + " WHERE created_at >= ? AND created_at < ? GROUP BY context",
                (RowCallbackHandler) rs -> { out.put(rs.getString("context"), rs.getLong("c")); }, ts(b.from()), ts(b.to()));
        return out;
    }

    public List<FeedbackCount> feedback(DashboardRange.Bounds b) {
        return jdbc.query("SELECT context, signal, COUNT(*) AS c FROM recommendation_feedback"
                        + " WHERE created_at >= ? AND created_at < ? GROUP BY context, signal",
                (rs, i) -> new FeedbackCount(rs.getString("context"), rs.getString("signal"), rs.getLong("c")),
                ts(b.from()), ts(b.to()));
    }

    public List<AppidCount> topImpressions(DashboardRange.Bounds b, int limit) {
        return jdbc.query("SELECT appid, COUNT(*) AS c FROM recommendation_impression"
                        + " WHERE created_at >= ? AND created_at < ? GROUP BY appid ORDER BY c DESC, appid LIMIT ?",
                (rs, i) -> new AppidCount(rs.getLong("appid"), rs.getLong("c")), ts(b.from()), ts(b.to()), limit);
    }
}
