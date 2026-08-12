package SteamGame.recommend.domain.feedback.service;

import SteamGame.recommend.domain.feedback.entity.FeedbackSignal;
import SteamGame.recommend.domain.feedback.repository.RecommendationFeedbackRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeedbackSuppressionServiceTest {

    RecommendationFeedbackRepository repository = mock(RecommendationFeedbackRepository.class);
    FeedbackSuppressionService service = new FeedbackSuppressionService(repository);

    @Test
    void mergesDownAppidsWithClientExcludes() {
        when(repository.findAppidsByClientIdAndSignal("c", FeedbackSignal.DOWN)).thenReturn(List.of(1L, 2L));

        Set<Long> result = service.resolveExcludes("c", Set.of(9L));

        assertThat(result).containsExactlyInAnyOrder(1L, 2L, 9L);
    }

    @Test
    void blankClientId_returnsClientExcludes_withoutQuery() {
        Set<Long> result = service.resolveExcludes("  ", Set.of(9L));

        assertThat(result).containsExactly(9L);
        verifyNoInteractions(repository);
    }

    @Test
    void nullClientId_returnsEmpty_forNullExcludes_withoutQuery() {
        Set<Long> result = service.resolveExcludes(null, null);

        assertThat(result).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void noDownFeedback_returnsClientExcludes() {
        when(repository.findAppidsByClientIdAndSignal("c", FeedbackSignal.DOWN)).thenReturn(List.of());

        Set<Long> result = service.resolveExcludes("c", Set.of(9L));

        assertThat(result).containsExactly(9L);
    }
}
