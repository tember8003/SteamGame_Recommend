package SteamGame.recommend.domain.library.dto;

import SteamGame.recommend.domain.library.LibraryFixtures;
import SteamGame.recommend.domain.library.analysis.GenreState;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Judged;
import SteamGame.recommend.domain.library.content.LibraryContent;
import SteamGame.recommend.domain.library.content.Phrase;
import SteamGame.recommend.domain.taste.content.Lang;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryViewMapperTest {

    private LibraryAnalysis analysisWithCrimeParams(Map<String, Object> params, int index) {
        LibraryAnalysis base = emptyBase();
        return new LibraryAnalysis(false, base.stats(), base.ledger(),
                new LibraryAnalysis.Verdict(new Judged("GENRE_LOYALIST", index, params), base.verdict().sentence()),
                base.gamerStats(), base.diagnosis(), null);
    }

    private LibraryAnalysis emptyBase() {
        return new LibraryAnalysis(false,
                new LibraryAnalysis.Stats(1, 1.0, 0, 0.0, null, 0, 0.0, 0, List.of()),
                new Judged("BALANCED", 0, Map.of()),
                new LibraryAnalysis.Verdict(new Judged("INNOCENT", 0, Map.of()),
                        new Judged("SENTENCE_INNOCENT", 0, Map.of())),
                List.of(new LibraryAnalysis.GamerStat(SteamGame.recommend.domain.library.analysis.StatCode.COOP, null)),
                new LibraryAnalysis.Diagnosis(null, List.of(), null),
                null);
    }

    @Test
    void topGroupKeyIsReplacedWithLanguageLabel() {
        LibraryContent content = LibraryFixtures.validContent();
        Map<String, Object> params = new HashMap<>();
        params.put("topGroup", "STRATEGY");
        params.put("topGroupRatioPct", 70);

        AnalyzeResponse ko = LibraryViewMapper.toAnalyzeResponse("a",
                new SteamGame.recommend.domain.library.steam.SteamProfile("1", "n", "u"),
                analysisWithCrimeParams(params, 0), content, Lang.KO);
        SharedAnalysisResponse en = LibraryViewMapper.toSharedResponse(analysisWithCrimeParams(params, 0), content, Lang.EN);

        assertThat(ko.verdict().crime().params()).containsEntry("topGroup", "STRATEGY ko").containsEntry("topGroupRatioPct", 70);
        assertThat(en.verdict().crime().params()).containsEntry("topGroup", "STRATEGY en");
    }

    @Test
    void emptyTopGroupStaysEmpty() {
        LibraryContent content = LibraryFixtures.validContent();
        SharedAnalysisResponse r = LibraryViewMapper.toSharedResponse(
                analysisWithCrimeParams(Map.of("topGroup", ""), 0), content, Lang.KO);
        assertThat(r.verdict().crime().params()).containsEntry("topGroup", "");
    }

    @Test
    void usesStoredPhraseIndex() {
        LibraryContent content = LibraryFixtures.validContent();
        Phrase second = content.phrases().get("GENRE_LOYALIST").get(1);
        SharedAnalysisResponse r = LibraryViewMapper.toSharedResponse(analysisWithCrimeParams(Map.of(), 1), content, Lang.EN);
        assertThat(r.verdict().crime().text()).isEqualTo(second.text().en());
    }

    @Test
    void freshPath_missingPhraseIndexFailsLoudly() {
        LibraryContent content = LibraryFixtures.validContent();
        SteamGame.recommend.domain.library.steam.SteamProfile p =
                new SteamGame.recommend.domain.library.steam.SteamProfile("1", "n", "u");
        assertThatThrownBy(() -> LibraryViewMapper.toAnalyzeResponse("a", p,
                analysisWithCrimeParams(Map.of(), 9), content, Lang.KO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sharedPath_outOfRangePhraseIndexIsClamped() {
        LibraryContent content = LibraryFixtures.validContent();
        List<Phrase> candidates = content.phrases().get("GENRE_LOYALIST");
        SharedAnalysisResponse r = LibraryViewMapper.toSharedResponse(analysisWithCrimeParams(Map.of(), 9), content, Lang.KO);
        assertThat(r.verdict().crime().text())
                .isEqualTo(candidates.get(Math.floorMod(9, candidates.size())).text().ko());
    }

    @Test
    void sharedPath_missingCodeStillFails() {
        LibraryContent content = LibraryFixtures.withPhrases(LibraryFixtures.validContent(), "GENRE_LOYALIST", null);
        assertThatThrownBy(() -> LibraryViewMapper.toSharedResponse(analysisWithCrimeParams(Map.of(), 0), content, Lang.KO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void gamerStatLabelUsesFirstCandidate() {
        LibraryContent content = LibraryFixtures.validContent();
        SharedAnalysisResponse r = LibraryViewMapper.toSharedResponse(emptyBase(), content, Lang.EN);
        assertThat(r.gamerStats().get(0).code()).isEqualTo("COOP");
        assertThat(r.gamerStats().get(0).value()).isNull();
        assertThat(r.gamerStats().get(0).label()).isEqualTo(content.phrases().get("STAT_COOP").get(0).text().en());
    }

    private static LibraryContent loyalistCandidates(Phrase... phrases) {
        return LibraryFixtures.withPhrases(LibraryFixtures.validContent(), "GENRE_LOYALIST", List.of(phrases));
    }

    @Test
    void sharedPath_skipsCandidateWhoseParamsWereNotStored() {
        // 저장 당시에는 topGroup만 있었는데 지금 0번 후보는 topGroupRatioPct도 쓴다
        LibraryContent content = loyalistCandidates(
                LibraryFixtures.phrase("A ko", "A en", "topGroup", "topGroupRatioPct"),
                LibraryFixtures.phrase("B ko", "B en", "topGroup"));
        Map<String, Object> stored = new HashMap<>();
        stored.put("topGroup", "STRATEGY");

        SharedAnalysisResponse r = LibraryViewMapper.toSharedResponse(analysisWithCrimeParams(stored, 0), content, Lang.EN);

        assertThat(r.verdict().crime().text()).isEqualTo("B en");
    }

    @Test
    void sharedPath_searchesFromFloorModIndexAndWraps() {
        LibraryContent content = loyalistCandidates(
                LibraryFixtures.phrase("A ko", "A en"),
                LibraryFixtures.phrase("B ko", "B en", "needed"),
                LibraryFixtures.phrase("C ko", "C en", "needed"));
        // 7 mod 3 은 1번이고 1번과 2번이 모두 채울 수 없어 0번으로 돌아온다
        SharedAnalysisResponse r = LibraryViewMapper.toSharedResponse(analysisWithCrimeParams(Map.of(), 7), content, Lang.EN);

        assertThat(r.verdict().crime().text()).isEqualTo("A en");
    }

    @Test
    void sharedPath_noFillableCandidateIsAContentDefect() {
        LibraryContent content = loyalistCandidates(
                LibraryFixtures.phrase("A ko", "A en", "needed"),
                LibraryFixtures.phrase("B ko", "B en", "needed"));

        assertThatThrownBy(() -> LibraryViewMapper.toSharedResponse(analysisWithCrimeParams(Map.of(), 0), content, Lang.EN))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sharedPath_unknownTopGroupKeyIsShownWithWarn() {
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        Logger logger = (Logger) LoggerFactory.getLogger(LibraryViewMapper.class);
        logger.addAppender(logs);
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("topGroup", "RENAMED_GROUP");

            SharedAnalysisResponse r = LibraryViewMapper.toSharedResponse(
                    analysisWithCrimeParams(params, 0), LibraryFixtures.validContent(), Lang.KO);

            assertThat(r.verdict().crime().params()).containsEntry("topGroup", "RENAMED_GROUP");
            assertThat(logs.list).anySatisfy(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.WARN);
                assertThat(e.getFormattedMessage()).contains("RENAMED_GROUP");
            });
        } finally {
            logger.detachAppender(logs);
        }
    }
    // 장르 결과의 필드를 하나씩 옮기는지, 분포 순서를 지키는지 확인한다
    @Test
    void genreProfileIsCopiedFieldByField() {
        LibraryAnalysis.GenreProfile g = new LibraryAnalysis.GenreProfile("SURVIVAL_CRAFTING", "RPG_ADVENTURE",
                GenreState.MIXED, 0.812, List.of(
                new LibraryAnalysis.GenreShare("SURVIVAL_CRAFTING", 0.52),
                new LibraryAnalysis.GenreShare("RPG_ADVENTURE", 0.45),
                new LibraryAnalysis.GenreShare("PUZZLE", 0.03)));
        LibraryAnalysis a = emptyBase().withGenreProfile(g);

        GenreProfileView v = LibraryViewMapper.toSharedResponse(a, LibraryFixtures.validContent(), Lang.KO).genreProfile();

        assertThat(v.primaryGenre()).isEqualTo("SURVIVAL_CRAFTING");
        assertThat(v.secondaryGenre()).isEqualTo("RPG_ADVENTURE");
        assertThat(v.state()).isEqualTo(GenreState.MIXED);
        assertThat(v.coverageRatio()).isEqualTo(0.812);
        assertThat(v.distribution()).containsExactly(
                new GenreProfileView.GenreShareView("SURVIVAL_CRAFTING", 0.52),
                new GenreProfileView.GenreShareView("RPG_ADVENTURE", 0.45),
                new GenreProfileView.GenreShareView("PUZZLE", 0.03));
        assertThat(LibraryViewMapper.toSharedResponse(emptyBase(), LibraryFixtures.validContent(), Lang.KO)
                .genreProfile()).isNull();
    }
}
