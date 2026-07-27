package SteamGame.recommend.infra.embedding;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import SteamGame.recommend.infra.api.GeminiEmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingBackfillServiceTest {

    final EmbeddingRepository embeddingRepository = mock(EmbeddingRepository.class);
    final GameRepository gameRepository = mock(GameRepository.class);
    final TagRepository tagRepository = mock(TagRepository.class);
    final GeminiEmbeddingService gemini = mock(GeminiEmbeddingService.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    EmbeddingProperties props() {
        EmbeddingProperties p = new EmbeddingProperties();
        p.setBatchSize(1);
        p.setRequestDelayMs(0);
        p.setRetryBackoffMs(0);   // 테스트에서 실제 대기 없이 재시도만 확인함
        p.setMaxBatchRetries(3);
        return p;
    }

    EmbeddingBackfillService service(EmbeddingProperties p) {
        return new EmbeddingBackfillService(embeddingRepository, gameRepository, tagRepository, gemini, p, jdbc);
    }

    private Game game(long appid) {
        Game g = mock(Game.class);
        when(g.getAppid()).thenReturn(appid);
        when(g.getName()).thenReturn("Game " + appid);
        return g;
    }

    @Test
    void batch_429ThenSuccess_backsOffThenEmbeds() {
        Game g1 = game(1L);
        when(embeddingRepository.selectNextToEmbed(anyInt()))
                .thenReturn(List.of(1L))
                .thenReturn(List.of());
        when(gameRepository.findByAppidIn(List.of(1L))).thenReturn(List.of(g1));
        when(tagRepository.findGameTagPairs(List.of(1L))).thenReturn(List.of());
        // 첫 배치는 429(빈 응답), 재시도에서 성공함
        when(gemini.embedBatch(anyList()))
                .thenReturn(List.of())
                .thenReturn(List.of(new float[]{0.1f, 0.2f}));

        EmbeddingBackfillService.EmbedResult result = service(props()).runBackfill();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
        verify(gemini, times(2)).embedBatch(anyList());   // 최초 1 + 재시도 1
        verify(embeddingRepository).updateEmbedding(eq(1L), any(float[].class));
    }

    @Test
    void batch_persistentFailure_givesUpAfterMaxRetries() {
        Game g1 = game(1L);
        // 두 번째 조회 빈 리스트는 방어용 — 현재는 apiDown break 로 도달 전 종료되나, 향후 변경 시 무한루프 방지
        when(embeddingRepository.selectNextToEmbed(anyInt())).thenReturn(List.of(1L)).thenReturn(List.of());
        when(gameRepository.findByAppidIn(List.of(1L))).thenReturn(List.of(g1));
        when(tagRepository.findGameTagPairs(List.of(1L))).thenReturn(List.of());
        when(gemini.embedBatch(anyList())).thenReturn(List.of());   // 계속 실패

        EmbeddingBackfillService.EmbedResult result = service(props()).runBackfill();

        // maxBatchRetries=3 → 최초 1 + 재시도 3 = 4회 호출 후 중단함
        verify(gemini, times(4)).embedBatch(anyList());
        assertThat(result.processed()).isEqualTo(0);
        verify(embeddingRepository, never()).updateEmbedding(anyLong(), any());
    }
}
