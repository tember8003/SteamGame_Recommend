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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
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
import static org.mockito.ArgumentMatchers.anyString;
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
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    Logger logger = (Logger) LoggerFactory.getLogger(TasteRecommendService.class);

    @BeforeEach
    void setUp() {
        service = new TasteRecommendService(loader, candidates, games, rarity);
        gmhs = loader.content().type("GMHS").orElseThrow();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private List<ILoggingEvent> logsAt(Level level) {
        return appender.list.stream().filter(e -> e.getLevel() == level).toList();
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
                    assertThat(e.getReason()).isEqualTo("TASTE_NO_MORE_GAMES");
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

    @Test
    void nullExcludesTreatedAsEmpty() {
        stubCandidates(candidateList(20));

        assertThat(service.recommend("GMHS", 100, null, null, null).games()).hasSize(5);

        ArgumentCaptor<Collection<Long>> exclude = ArgumentCaptor.forClass(Collection.class);
        org.mockito.Mockito.verify(candidates).findCandidates(anyList(), anyList(), anyInt(), any(), any(), exclude.capture(), anyInt());
        assertThat(exclude.getValue()).containsExactlyInAnyOrderElementsOf(gmhs.featuredAppids());
    }

    @Test
    void allWeightsZero_logsErrorWithTypeCode() {
        stubCandidates(candidateList(20));
        when(rarity.rarity(anyString())).thenReturn(0.0);

        service.recommend("GMHS", 100, null, null, Set.of());

        assertThat(logsAt(Level.ERROR)).anySatisfy(e ->
                assertThat(e.getFormattedMessage()).contains("GMHS").contains("no usable tag weights"));
    }

    @Test
    void positiveWeights_noError() {
        stubCandidates(candidateList(20));
        when(rarity.rarity(anyString())).thenReturn(1.0);

        service.recommend("GMHS", 100, null, null, Set.of());

        assertThat(logsAt(Level.ERROR)).isEmpty();
    }

    @Test
    void noCandidates_logsWarnWithTypeAndFilters() {
        stubCandidates(List.of());

        assertThatThrownBy(() -> service.recommend("GMHS", 77, true, false, Set.of(1L, 2L)))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(logsAt(Level.WARN)).anySatisfy(e -> assertThat(e.getFormattedMessage())
                .contains("GMHS").contains("review=77").contains("korean=true").contains("free=false")
                .contains("excludeCount=" + (2 + gmhs.featuredAppids().size())));
    }

    @Test
    void featuredGames_oneWarnListingAllMissing() {
        TasteType type = new TasteType("GMHS", gmhs.name(), gmhs.work(), gmhs.workAppid(), gmhs.oneLiner(),
                gmhs.description(), gmhs.traits(), gmhs.tagWeights(), List.of(3L, 1L, 2L), gmhs.share());
        when(games.findByAppidIn(anyList())).thenReturn(List.of(game(1)));

        service.featuredGames(type);

        assertThat(logsAt(Level.WARN)).hasSize(1);
        assertThat(logsAt(Level.WARN).get(0).getFormattedMessage()).contains("GMHS").contains("[2, 3]");
    }

    @Test
    void checkFeaturedGames_errorsPerTypeWithMissingAppids() {
        TasteType first = loader.content().types().get(0);
        Long missingId = first.featuredAppids().get(0);
        when(games.findByAppidIn(anyList())).thenAnswer(inv -> ((List<Long>) inv.getArgument(0)).stream()
                .filter(id -> !missingId.equals(id))
                .map(TasteRecommendServiceTest::game).toList());

        service.checkFeaturedGames();

        assertThat(logsAt(Level.ERROR)).anySatisfy(e -> assertThat(e.getFormattedMessage())
                .contains(first.code()).contains(missingId.toString()));
    }

    @Test
    void checkFeaturedGames_allPresent_noError() {
        when(games.findByAppidIn(anyList())).thenAnswer(inv -> ((List<Long>) inv.getArgument(0)).stream()
                .map(TasteRecommendServiceTest::game).toList());

        service.checkFeaturedGames();

        assertThat(logsAt(Level.ERROR)).isEmpty();
    }

    @Test
    void checkFeaturedGames_dbFailureLoggedNotThrown() {
        when(games.findByAppidIn(anyList())).thenThrow(new IllegalStateException("db down"));

        service.checkFeaturedGames();

        assertThat(logsAt(Level.ERROR)).hasSize(1);
    }
}
