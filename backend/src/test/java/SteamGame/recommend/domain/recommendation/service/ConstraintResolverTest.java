package SteamGame.recommend.domain.recommendation.service;

import SteamGame.recommend.domain.recommendation.dto.MatchRequestDTO;
import SteamGame.recommend.domain.recommendation.dto.ParsedConstraints;
import SteamGame.recommend.domain.recommendation.dto.RecommendationCriteria;
import SteamGame.recommend.domain.recommendation.dto.SessionLength;
import SteamGame.recommend.infra.api.GeminiConstraintService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConstraintResolverTest {

    GeminiConstraintService parser = mock(GeminiConstraintService.class);
    ConstraintResolver resolver = new ConstraintResolver(parser);

    @Test
    void formWinsOverNaturalLanguage() {
        when(parser.parseConstraints(any())).thenReturn(
                new ParsedConstraints(3, 50, SessionLength.LONG, true, "느긋한", List.of("Gore")));
        MatchRequestDTO req = new MatchRequestDTO();
        req.setRawText("아무 문장");
        req.setPlayers(4);
        req.setBudgetUsd(20);
        req.setExcludeTags(List.of("Horror"));

        RecommendationCriteria c = resolver.resolve(req);

        assertThat(c.getPlayers()).isEqualTo(4);
        assertThat(c.getBudgetCents()).isEqualTo(2000);
        assertThat(c.getSessionLength()).isEqualTo(SessionLength.LONG);
        assertThat(c.getExcludeTags()).contains("Horror", "Gore");
        assertThat(c.isNlParseFailed()).isFalse();
    }

    @Test
    void blankRawTextSkipsParser() {
        MatchRequestDTO req = new MatchRequestDTO();
        req.setPlayers(2);

        resolver.resolve(req);

        verifyNoInteractions(parser);
    }

    @Test
    void parserFailureSetsFlagAndUsesFormOnly() {
        when(parser.parseConstraints(any())).thenReturn(null);
        MatchRequestDTO req = new MatchRequestDTO();
        req.setRawText("문장");
        req.setBudgetUsd(10);

        RecommendationCriteria c = resolver.resolve(req);

        assertThat(c.isNlParseFailed()).isTrue();
        assertThat(c.getBudgetCents()).isEqualTo(1000);
    }

    @Test
    void emptyRequestIsEmptyCriteria() {
        RecommendationCriteria c = resolver.resolve(new MatchRequestDTO());

        assertThat(c.isEmpty()).isTrue();
    }

    @Test
    void moodTextCombinesFormTagsAndParsedMood() {
        when(parser.parseConstraints(any())).thenReturn(
                new ParsedConstraints(null, null, null, null, "느긋한 분위기", null));
        MatchRequestDTO req = new MatchRequestDTO();
        req.setRawText("문장");
        req.setMoodTags(List.of("코옵", "캐주얼"));

        RecommendationCriteria c = resolver.resolve(req);

        assertThat(c.getMoodText()).isEqualTo("코옵 캐주얼 느긋한 분위기");
        assertThat(c.hasMood()).isTrue();
    }
}
