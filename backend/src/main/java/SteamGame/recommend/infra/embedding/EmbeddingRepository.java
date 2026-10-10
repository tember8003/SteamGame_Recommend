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

    // 임베딩이 없는 게임을 먼저, 그다음 리뷰 많은 순으로 고름, 재임베딩 대상이 신작을 밀어내지 않게 함
    public List<Long> selectNextToEmbed(int n) {
        return jdbc.queryForList("""
            SELECT appid FROM games
            WHERE embedding IS NULL
               OR (last_crawled_at IS NOT NULL
                   AND (embedding_updated_at IS NULL OR last_crawled_at > embedding_updated_at))
            ORDER BY (embedding IS NULL) DESC, review_count DESC NULLS LAST
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

    // 태그 구조 벡터(GNN)를 pgvector 리터럴 텍스트로 읽음, 없으면 null
    // 최근접 쿼리에 리터럴로 그대로 넘겨 float 변환을 오가지 않게 함
    public String findTagEmbeddingText(long appid) {
        return jdbc.query(
                "SELECT tag_embedding::text FROM games WHERE appid = ? AND tag_embedding IS NOT NULL",
                rs -> rs.next() ? rs.getString(1) : null,
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

    // seed 임베딩 최근접 k개 appid, 가까운 순, seed 는 제외한다
    // seed 벡터를 먼저 읽어 리터럴 오버로드에 넘김, 임베딩 없거나 k<=0 이면 빈 목록
    public List<Long> findNeighborAppids(long seedAppid, int k) {
        if (k <= 0) {
            return List.of();
        }
        String seedVec = jdbc.query(
                "SELECT embedding::text FROM games WHERE appid = ? AND embedding IS NOT NULL",
                rs -> rs.next() ? rs.getString(1) : null,
                seedAppid);
        return findNeighborAppids(seedVec, seedAppid, k);
    }

    // 이미 읽어둔 seed 벡터 리터럴로 최근접 k개 appid, 가까운 순, seed 는 제외한다
    // 여러 seed 를 돌 때 점 조회를 아끼려고 벡터를 밖에서 받음, 리터럴로 바인딩해야 HNSW 인덱스를 탄다
    // 벡터가 null 이거나 k<=0 이면 빈 목록
    public List<Long> findNeighborAppids(String seedVecText, long seedAppid, int k) {
        if (seedVecText == null || k <= 0) {
            return List.of();
        }
        return jdbc.queryForList("""
                SELECT appid FROM games
                WHERE embedding IS NOT NULL AND appid <> ?
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?""",
                Long.class, seedAppid, seedVecText, k);
    }

    // 여러 appid 의 임베딩을 pgvector 리터럴 텍스트로 한 번에 읽음, 임베딩 없는 appid 는 맵에서 빠짐
    // 이웃 조회 오버로드에 넘길 시드 벡터를 미리 배치로 확보하는 용도, float 파싱을 건너뛴다
    // 람다는 RowCallbackHandler 로 캐스팅해야 함, 안 하면 값 반환 람다가 ResultSetExtractor 로 해석돼 행 커서 이전에 1회만 호출됨
    public Map<Long, String> findEmbeddingTextsByAppids(List<Long> appids) {
        if (appids == null || appids.isEmpty()) {
            return Map.of();
        }
        String placeholders = appids.stream().map(x -> "?").collect(Collectors.joining(","));
        String sql = "SELECT appid, embedding::text FROM games WHERE appid IN (" + placeholders
                + ") AND embedding IS NOT NULL";
        Map<Long, String> out = new HashMap<>();
        jdbc.query(sql, (RowCallbackHandler) rs ->
                out.put(rs.getLong(1), rs.getString(2)), appids.toArray());
        return out;
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
