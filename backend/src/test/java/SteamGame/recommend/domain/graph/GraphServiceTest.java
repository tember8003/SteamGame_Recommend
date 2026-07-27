package SteamGame.recommend.domain.graph;

import SteamGame.recommend.domain.graph.dto.GraphNeighbor;
import SteamGame.recommend.domain.graph.dto.GraphNode;
import SteamGame.recommend.domain.graph.dto.NeighborsResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphServiceTest {

    final GraphRepository repo = mock(GraphRepository.class);
    final GraphService service = new GraphService(repo);

    @Test
    void search_blankOrShort_returnsEmpty_withoutHittingRepo() {
        assertThat(service.search("")).isEmpty();
        assertThat(service.search(" a ")).isEmpty();   // trim 후 2자 미만
        verify(repo, never()).searchByName(any(), anyInt());
    }

    @Test
    void search_delegatesTrimmed() {
        when(repo.searchByName("counter", 10)).thenReturn(List.of(new GraphNode(1L, "C", "i")));
        assertThat(service.search("  counter  ")).hasSize(1);
    }

    @Test
    void neighbors_clampsK_andWraps() {
        when(repo.findNeighbors(eq(730L), anyInt()))
                .thenReturn(List.of(new GraphNeighbor(240L, "N", "i", "Action", 0.9)));

        NeighborsResponse r = service.neighbors(730L, 999);   // 상한 15 로 클램프

        assertThat(r.appid()).isEqualTo(730L);
        assertThat(r.neighbors()).hasSize(1);
        verify(repo).findNeighbors(730L, 15);
    }

    @Test
    void neighbors_nullK_usesDefault8() {
        when(repo.findNeighbors(eq(1L), anyInt())).thenReturn(List.of());
        service.neighbors(1L, null);
        verify(repo).findNeighbors(1L, 8);
    }

    @Test
    void neighbors_zeroK_clampsToOne() {
        when(repo.findNeighbors(eq(1L), anyInt())).thenReturn(List.of());
        service.neighbors(1L, 0);
        verify(repo).findNeighbors(1L, 1);
    }
}
