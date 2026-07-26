package SteamGame.recommend.controller;

import SteamGame.recommend.domain.graph.GraphService;
import SteamGame.recommend.domain.graph.dto.GraphNode;
import SteamGame.recommend.domain.graph.dto.NeighborsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "graph", description = "게임 유사도 그래프 API")
@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    @Operation(summary = "임베딩 있는 게임 이름 검색 (그래프 시작점)")
    @GetMapping("/search")
    public ResponseEntity<List<GraphNode>> search(@RequestParam(value = "q", required = false) String q) {
        return ResponseEntity.ok(graphService.search(q));
    }

    @Operation(summary = "한 게임의 임베딩 최근접 이웃")
    @GetMapping("/neighbors")
    public ResponseEntity<NeighborsResponse> neighbors(@RequestParam("appid") long appid,
                                                       @RequestParam(value = "k", required = false) Integer k) {
        return ResponseEntity.ok(graphService.neighbors(appid, k));
    }
}
