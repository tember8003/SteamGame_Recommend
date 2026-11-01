package SteamGame.recommend.domain.taste.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class TasteCandidateRepository {

    // 필터 의미는 GameRepository.findRandomGameByTags와 같다
    // 제외 목록은 빈 배열이면 ALL 조건이 항상 참이라 따로 가르지 않는다
    private static final String SQL = """
            SELECT g.appid AS appid,
                   SUM(w.weight) AS score,
                   array_agg(t.name ORDER BY w.weight DESC, t.name) AS matched
            FROM games g
            JOIN reviews r ON r.appid = g.appid
            JOIN game_tags gt ON gt.game_id = g.id
            JOIN tags t ON t.id = gt.tag_id
            JOIN unnest(CAST(:tags AS text[]), CAST(:weights AS float8[])) AS w(tag, weight) ON w.tag = t.name
            WHERE r.total_reviews >= :review
              AND (CAST(:korean AS boolean) IS NULL OR g.korean_support = CAST(:korean AS boolean))
              AND (CAST(:free AS boolean) IS NULL OR g.is_free = CAST(:free AS boolean))
              AND g.appid <> ALL (CAST(:excluded AS bigint[]))
            GROUP BY g.appid, r.total_reviews
            HAVING SUM(w.weight) > 0
            ORDER BY score DESC, r.total_reviews DESC, g.appid
            LIMIT :k
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public TasteCandidateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // weights는 희소도를 이미 곱한 값이고 tags와 길이가 같아야 한다
    public List<TasteCandidate> findCandidates(List<String> tags, List<Double> weights, int review,
                                               Boolean korean, Boolean free,
                                               Collection<Long> excludeAppids, int k) {
        if (tags.size() != weights.size()) {
            throw new IllegalArgumentException("tags and weights must have the same size");
        }
        if (tags.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tags", textArray(tags))
                .addValue("weights", weights.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}")))
                .addValue("review", review)
                .addValue("korean", korean)
                .addValue("free", free)
                .addValue("excluded", excludeAppids.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}")))
                .addValue("k", k);
        return jdbc.query(SQL, params, (rs, i) -> new TasteCandidate(
                rs.getLong("appid"),
                rs.getDouble("score"),
                List.of((String[]) toArray(rs.getArray("matched")))));
    }

    private static Object toArray(Array array) throws java.sql.SQLException {
        try {
            return array.getArray();
        } finally {
            array.free();
        }
    }

    // 태그 이름에 쉼표나 따옴표가 있어도 깨지지 않게 원소마다 따옴표로 감싼다
    private static String textArray(List<String> values) {
        return values.stream()
                .map(v -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }
}
