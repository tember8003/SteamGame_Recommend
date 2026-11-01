package SteamGame.recommend.domain.taste.service;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.recommendation.dto.ExplanationItem;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import SteamGame.recommend.domain.taste.content.TasteType;
import SteamGame.recommend.domain.taste.repository.TasteCandidate;
import SteamGame.recommend.domain.taste.repository.TasteCandidateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TasteRecommendServiceTest {

    TasteCandidateRepository candidates = mock(TasteCandidateRepository.class);
    GameRepository games = mock(GameRepository.class);
    TagRarityCache rarity = mock(TagRarityCache.class);
    TasteContentLoader loader = new TasteContentLoader(new ObjectMapper());
    TasteRecommendService service;
    TasteType gmhs;

    @BeforeEach
    void setUp() {
        service = new TasteRecommendService(loader, candidates, games, rarity);
        gmhs = loader.content().type("GMHS").orElseThrow();
    }

    private static Game game(long appid) {
        Game g = new Game();
        g.setAppid(appid);
        g.setName("game" + appid);
        return g;
    }

    private List<TasteCandidate> candidateList(int n) {
        List<TasteCandidate> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add(new TasteCandidate(1000 + i, 100 - i, List.of("T" + i, "A" + i, "B" + i, "C" + i)));
        }
        return list;
    }

    private void stubCandidates(List<TasteCandidate> list) {
        when(candidates.findCandidates(anyList(), anyList(), anyInt(), any(), any(), any(), anyInt())).thenReturn(list);
        when(games.findByAppidIn(anyList())).thenAnswer(inv -> ((List<Long>) inv.getArgument(0)).stream()
                .map(TasteRecommendServiceTest::game).toList());
    }

    @Test
    void passesRarityWeightedWeights() {
        stubCandidates(candidateList(20));
        String firstTag = gmhs.tagWeights().get(0).tag();
        double w = gmhs.tagWeights().get(0).weight();
        when(rarity.rarity(firstTag)).thenReturn(2.5);

        service.recommend("GMHS", 100, null, null, Set.of());

        ArgumentCaptor<List<Double>> weights = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> tags = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(candidates).findCandidates(tags.capture(), weights.capture(), eq(100), any(), any(), any(), anyInt());
        assertThat(tags.getValue().get(0)).isEqualTo(firstTag);
        assertThat(weights.getValue().get(0)).isEqualTo(w * 2.5);
    }

    @Test
    void usesDefaults() {
        stubCandidates(candidateList(20));

        service.recommend("GMHS", 100, null, null, Set.of());

        org.mockito.Mockito.verify(candidates).findCandidates(anyList(), anyList(), anyInt(), any(), any(), any(), eq(60));
    }

    @Test
    void excludesFeaturedAndClientExcludes() {
        stubCandidates(candidateList(20));

        service.recommend("GMHS", 100, null, null, Set.of(777L, 888L));

        ArgumentCaptor<Collection<Long>> exclude = ArgumentCaptor.forClass(Collection.class);
        org.mockito.Mockito.verify(candidates).findCandidates(anyList(), anyList(), anyInt(), any(), any(), exclude.capture(), anyInt());
        assertThat(exclude.getValue()).containsAll(gmhs.featuredAppids()).contains(777L, 888L);
    }

    @Test
    void returnsFiveWithExplanations() {
        stubCandidates(candidateList(20));

        TasteRecommendResult result = service.recommend("GMHS", 100, null, null, Set.of());

        assertThat(result.games()).hasSize(5);
        assertThat(result.explanations()).hasSize(5);
        for (int i = 0; i < 5; i++) {
            SteamAppDTO g = result.games().get(i);
            GameExplanation e = result.explanations().get(i);
            assertThat(e.appid()).isEqualTo(g.getAppid());
            long n = g.getAppid() - 1000;
            assertThat(e.items()).containsExactly(new ExplanationItem("TASTE_TAG_MATCH",
                    Map.of("tags", List.of("T" + n, "A" + n, "B" + n))));
        }
    }

    @Test
    void unknownCode404() {
        assertThatThrownBy(() -> service.recommend("XXXX", 100, null, null, Set.of()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getReason()).isEqualTo("TASTE_TYPE_NOT_FOUND");
                });
    }

    @Test
    void lowercaseCodeAccepted() {
        stubCandidates(candidateList(20));

        assertThat(service.recommend("gmhs", 100, null, null, Set.of()).games()).hasSize(5);
    }

    @Test
    void noCandidates404() {
        stubCandidates(List.of());

        assertThatThrownBy(() -> service.recommend("GMHS", 100, null, null, Set.of()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getReason()).isEqualTo("No games found for this taste type.");
                });
    }

    @Test
    void featuredGames_dropsMissingAndKeepsOrder() {
        TasteType type = new TasteType("GMHS", gmhs.name(), gmhs.work(), gmhs.workAppid(), gmhs.oneLiner(),
                gmhs.description(), gmhs.traits(), gmhs.tagWeights(), List.of(3L, 1L, 2L), gmhs.share());
        when(games.findByAppidIn(anyList())).thenReturn(List.of(game(1), game(3)));

        List<SteamAppDTO> result = service.featuredGames(type);

        assertThat(result).extracting(SteamAppDTO::getAppid).containsExactly(3L, 1L);
    }
}
