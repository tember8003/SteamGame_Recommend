package SteamGame.recommend.domain.graph;

import SteamGame.recommend.domain.graph.dto.GraphNeighbor;
import SteamGame.recommend.domain.graph.dto.GraphNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

// 그래프 전용 pgvector 조회. 임베딩은 Game 엔티티 미매핑이라 JdbcTemplate 네이티브로 처리한다
@Repository
public class GraphRepository {

    private final JdbcTemplate jdbc;

    public GraphRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 이름 부분매칭 + 임베딩 있는 게임만 노출함 (그래프로 그릴 수 있는 것만)
    public List<GraphNode> searchByName(String q, int limit) {
        return jdbc.query("""
                SELECT appid, name, image_url FROM games
                WHERE embedding IS NOT NULL AND name ILIKE ?
                ORDER BY review_count DESC NULLS LAST
                LIMIT ?""",
                (rs, i) -> new GraphNode(rs.getLong("appid"), rs.getString("name"), rs.getString("image_url")),
                "%" + q + "%", limit);
    }

    // seed 임베딩 최근접 K개, seed 제외, similarity=1−코사인거리. seed 임베딩을 먼저 조회해 리터럴로 바인딩함 → HNSW 인덱스 사용(상관 서브쿼리는 seq scan)
    public List<GraphNeighbor> findNeighbors(long seedAppid, int k) {
        String seedVec = jdbc.query(
                "SELECT embedding::text FROM games WHERE appid = ? AND embedding IS NOT NULL",
                rs -> rs.next() ? rs.getString(1) : null,
                seedAppid);
        if (seedVec == null) {
            return List.of();
        }
        return jdbc.query("""
                SELECT g.appid, g.name, g.image_url, g.genres,
                       (1 - (g.embedding <=> CAST(? AS vector))) AS similarity
                FROM games g
                WHERE g.embedding IS NOT NULL AND g.appid <> ?
                ORDER BY g.embedding <=> CAST(? AS vector)
                LIMIT ?""",
                (rs, i) -> new GraphNeighbor(rs.getLong("appid"), rs.getString("name"),
                        rs.getString("image_url"), rs.getString("genres"), rs.getDouble("similarity")),
                seedVec, seedAppid, seedVec, k);
    }
}
