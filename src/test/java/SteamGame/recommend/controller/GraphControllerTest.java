package SteamGame.recommend.controller;

import SteamGame.recommend.config.RateLimitProperties;
import SteamGame.recommend.config.SecurityConfig;
import SteamGame.recommend.domain.graph.GraphService;
import SteamGame.recommend.domain.graph.dto.GraphNeighbor;
import SteamGame.recommend.domain.graph.dto.GraphNode;
import SteamGame.recommend.domain.graph.dto.NeighborsResponse;
import SteamGame.recommend.infra.web.ClientIpResolver;
import SteamGame.recommend.infra.web.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GraphController.class)
@Import({SecurityConfig.class, RateLimitFilter.class, RateLimitProperties.class, ClientIpResolver.class})
class GraphControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean GraphService graphService;

    @Test
    void search_returnsNodes() throws Exception {
        given(graphService.search("counter")).willReturn(List.of(new GraphNode(730L, "Counter-Strike 2", "i")));
        mockMvc.perform(get("/api/graph/search").param("q", "counter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appid").value(730))
                .andExpect(jsonPath("$[0].name").value("Counter-Strike 2"));
    }

    @Test
    void neighbors_returnsResponse() throws Exception {
        given(graphService.neighbors(any(Long.class), any()))
                .willReturn(new NeighborsResponse(730L, List.of(new GraphNeighbor(240L, "N", "i", "Action", 0.9))));
        mockMvc.perform(get("/api/graph/neighbors").param("appid", "730").param("k", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appid").value(730))
                .andExpect(jsonPath("$.neighbors[0].appid").value(240))
                .andExpect(jsonPath("$.neighbors[0].similarity").value(0.9));
    }
}
