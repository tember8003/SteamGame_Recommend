package SteamGame.recommend.domain.impression.service;

import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.impression.entity.RecommendationImpression;
import SteamGame.recommend.domain.impression.repository.RecommendationImpressionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImpressionServiceTest {

    RecommendationImpressionRepository repo = mock(RecommendationImpressionRepository.class);
    ImpressionService service = new ImpressionService(repo);
    // 단위 테스트는 서비스를 직접 호출해 @Async 프록시를 거치지 않으므로 동기, 결정적임

    @Test
    @SuppressWarnings("unchecked")
    void recordsOneRowPerAppidWithAscendingRank() {
        service.record("cid", "steam1", FeedbackContext.MATCH, List.of(10L, 20L, 30L));

        ArgumentCaptor<List<RecommendationImpression>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(captor.capture());
        List<RecommendationImpression> rows = captor.getValue();

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(RecommendationImpression::getAppid).containsExactly(10L, 20L, 30L);
        assertThat(rows).extracting(RecommendationImpression::getRankPosition).containsExactly(1, 2, 3);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getClientId()).isEqualTo("cid");
            assertThat(r.getSteamId()).isEqualTo("steam1");
            assertThat(r.getContext()).isEqualTo(FeedbackContext.MATCH);
        });
    }

    @Test
    void blankOrNullClientIdIsNoOp() {
        service.record("   ", null, FeedbackContext.INPUT, List.of(1L));
        service.record(null, null, FeedbackContext.INPUT, List.of(1L));
        verify(repo, never()).saveAll(anyList());
    }

    @Test
    void emptyOrNullAppidsIsNoOp() {
        service.record("cid", null, FeedbackContext.RANDOM, List.of());
        service.record("cid", null, FeedbackContext.RANDOM, null);
        verify(repo, never()).saveAll(anyList());
    }

    @Test
    void swallowsRepositoryFailure() {
        when(repo.saveAll(any())).thenThrow(new RuntimeException("db down"));
        // 적재 실패가 밖으로 새어 추천 응답을 깨면 안 됨
        assertThatCode(() -> service.record("cid", null, FeedbackContext.SIMILAR, List.of(1L)))
                .doesNotThrowAnyException();
    }
}
