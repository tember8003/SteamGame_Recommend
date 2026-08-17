package SteamGame.recommend.infra.embedding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * games.embedding 벡터 I/O 전담. hibernate-vector 없이 JdbcTemplate + 네이티브 SQL 로 처리한다.
 */
@Repository
public class EmbeddingRepository {

    private final JdbcTemplate jdbc;

    public EmbeddingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 임베딩이 없거나(신규) 마지막 크롤 이후 갱신 안 된(stale) 게임 n개, 인기순
    public List<Long> selectNextToEmbed(int n) {
        return jdbc.queryForList("""
            SELECT appid FROM games
            WHERE embedding IS NULL
               OR (last_crawled_at IS NOT NULL
                   AND (embedding_updated_at IS NULL OR last_crawled_at > embedding_updated_at))
            ORDER BY owners_estimate DESC NULLS LAST
            LIMIT ?""", Long.class, n);
    }

    // 벡터를 pgvector 리터럴로 바인딩해 저장
    public void updateEmbedding(long appid, float[] vec) {
        jdbc.update(
                "UPDATE games SET embedding = CAST(? AS vector), embedding_updated_at = now() WHERE appid = ?",
                VectorLiterals.toPgVector(vec), appid);
    }

    // 단일 게임 임베딩 (유사게임 시드용), 없으면 null
    public float[] findEmbeddingByAppid(long appid) {
        return jdbc.query(
                "SELECT embedding::text FROM games WHERE appid = ? AND embedding IS NOT NULL",
                rs -> rs.next() ? VectorLiterals.parse(rs.getString(1)) : null,
                appid);
    }

    // 보유/최근 게임들의 평균 임베딩 (taste 벡터), 매칭 임베딩이 없으면 null
    public float[] findMeanEmbeddingByAppids(List<Long> appids) {
        if (appids == null || appids.isEmpty()) {
            return null;
        }
        String placeholders = appids.stream().map(x -> "?").collect(Collectors.joining(","));
        String sql = "SELECT AVG(embedding)::text FROM games WHERE appid IN (" + placeholders
                + ") AND embedding IS NOT NULL";
        return jdbc.query(sql,
                rs -> rs.next() ? VectorLiterals.parse(rs.getString(1)) : null,
                appids.toArray());
    }

    // 여러 appid 의 임베딩을 한 번에 읽음, 임베딩 없는 appid 는 맵에서 빠짐
    // 람다는 RowCallbackHandler 로 캐스팅해야 함, 안 하면 값 반환 람다가 ResultSetExtractor 로 해석돼 행 커서 이전에 1회만 호출됨
    public Map<Long, float[]> findEmbeddingsByAppids(List<Long> appids) {
        if (appids == null || appids.isEmpty()) {
            return Map.of();
        }
        String placeholders = appids.stream().map(x -> "?").collect(Collectors.joining(","));
        String sql = "SELECT appid, embedding::text FROM games WHERE appid IN (" + placeholders
                + ") AND embedding IS NOT NULL";
        Map<Long, float[]> out = new HashMap<>();
        jdbc.query(sql, (RowCallbackHandler) rs ->
                out.put(rs.getLong(1), VectorLiterals.parse(rs.getString(2))), appids.toArray());
        return out;
    }
}
