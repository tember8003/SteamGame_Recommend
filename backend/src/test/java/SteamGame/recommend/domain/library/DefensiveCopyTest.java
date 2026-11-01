package SteamGame.recommend.domain.library;

import SteamGame.recommend.domain.library.analysis.GenreState;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Diagnosis;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.GamerStat;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Judged;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Stats;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.TagShare;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.TopGame;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Verdict;
import SteamGame.recommend.domain.library.analysis.StatCode;
import SteamGame.recommend.domain.library.content.LibraryContent;
import SteamGame.recommend.domain.library.steam.OwnedGame;
import SteamGame.recommend.domain.library.steam.SteamLibraryResult;
import SteamGame.recommend.domain.library.steam.SteamProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 캐시와 싱글톤으로 오래 쓰이는 값 객체가 만든 쪽의 목록을 그대로 잡고 있지 않은지 확인한다
class DefensiveCopyTest {

    private static Judged judged(Map<String, Object> params) {
        return new Judged("BALANCED", 0, params);
    }

    @Test
    void analysisCopiesNestedListsAndKeepsParamOrder() {
        List<TagShare> tags = new ArrayList<>(List.of(new TagShare("a", 1.0, 0.5)));
        List<TopGame> concentrated = new ArrayList<>(List.of(new TopGame(1, "g", 1.0, 0.5)));
        List<GamerStat> stats = new ArrayList<>(List.of(new GamerStat(StatCode.COOP, 1)));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("z", 1);
        params.put("a", 2);
        params.put("m", 3);

        LibraryAnalysis analysis = new LibraryAnalysis(false,
                new Stats(1, 1.0, 0, 0.0, null, 0, 0.0, 0, tags),
                judged(params),
                new Verdict(judged(Map.of()), judged(Map.of())),
                stats,
                new Diagnosis(null, concentrated, null),
                null);
        tags.clear();
        concentrated.clear();
        stats.clear();
        params.put("late", 4);

        assertThat(analysis.stats().topTags()).hasSize(1);
        assertThat(analysis.diagnosis().concentratedGames()).hasSize(1);
        assertThat(analysis.gamerStats()).hasSize(1);
        assertThat(analysis.ledger().params()).containsExactly(
                Map.entry("z", 1), Map.entry("a", 2), Map.entry("m", 3));
        assertThatThrownBy(() -> analysis.ledger().params().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> analysis.stats().topTags().add(new TagShare("b", 1.0, 0.5)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void analysisRejectsMissingRequiredParts() {
        assertThatThrownBy(() -> new LibraryAnalysis(false, null, judged(Map.of()),
                new Verdict(judged(Map.of()), judged(Map.of())), List.of(), new Diagnosis(null, List.of(), null), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GamerStat(null, 1)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void foundCopiesGamesAndRejectsNulls() {
        List<OwnedGame> games = new ArrayList<>(List.of(new OwnedGame(1, "a", 1, 0, null)));
        SteamProfile profile = new SteamProfile("1", "n", "u");

        SteamLibraryResult.Found found = new SteamLibraryResult.Found(profile, games);
        games.clear();

        assertThat(found.games()).hasSize(1);
        assertThatThrownBy(() -> new SteamLibraryResult.Found(profile, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SteamLibraryResult.Found(null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SteamLibraryResult.Unavailable(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void contentInnerListsCannotBeChanged() {
        LibraryContent content = LibraryFixtures.validContent();

        assertThatThrownBy(() -> content.tagGroups().get("STRATEGY").add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> content.phrases().get("BALANCED").clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> content.tagGroups().put("x", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
    @Test
    void genreProfileRejectsInconsistentGenres() {
        assertThatThrownBy(() -> new LibraryAnalysis.GenreProfile(null, null, GenreState.READY, 1.0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LibraryAnalysis.GenreProfile("PUZZLE", null, GenreState.INSUFFICIENT, 0.1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LibraryAnalysis.GenreProfile(null, "PUZZLE", GenreState.INSUFFICIENT, 0.1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
