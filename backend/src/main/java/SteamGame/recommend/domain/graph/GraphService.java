package SteamGame.recommend.domain.graph;

import SteamGame.recommend.domain.graph.dto.GraphNode;
import SteamGame.recommend.domain.graph.dto.NeighborsResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GraphService {

    private static final int SEARCH_LIMIT = 10;
    private static final int DEFAULT_K = 8;
    private static final int MAX_K = 15;

    private final GraphRepository repo;

    public GraphService(GraphRepository repo) {
        this.repo = repo;
    }

    // 2자 미만 질의는 repo 조회 없이 빈 결과 반환함 (자동완성 잡음 방지)
    public List<GraphNode> search(String q) {
        String trimmed = q == null ? "" : q.trim();
        if (trimmed.length() < 2) {
            return List.of();
        }
        return repo.searchByName(trimmed, SEARCH_LIMIT);
    }

    public NeighborsResponse neighbors(long appid, Integer k) {
        int clamped = k == null ? DEFAULT_K : Math.max(1, Math.min(MAX_K, k));
        return new NeighborsResponse(appid, repo.findNeighbors(appid, clamped));
    }
}
