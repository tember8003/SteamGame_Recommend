package SteamGame.recommend.domain.library.dto;

import SteamGame.recommend.domain.library.LibraryFixtures;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Judged;
import SteamGame.recommend.domain.library.content.LibraryContent;
import SteamGame.recommend.domain.library.content.Phrase;
import SteamGame.recommend.domain.taste.content.Lang;
import org.junit.jupiter.api.Test;

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
                base.gamerStats(), base.diagnosis());
    }

    private LibraryAnalysis emptyBase() {
        return new LibraryAnalysis(false,
                new LibraryAnalysis.Stats(1, 1.0, 0, 0.0, null, 0, 0.0, 0, List.of()),
                new Judged("BALANCED", 0, Map.of()),
                new LibraryAnalysis.Verdict(new Judged("INNOCENT", 0, Map.of()),
                        new Judged("SENTENCE_INNOCENT", 0, Map.of())),
                List.of(new LibraryAnalysis.GamerStat(SteamGame.recommend.domain.library.analysis.StatCode.COOP, null)),
                new LibraryAnalysis.Diagnosis(null, List.of(), null));
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
}
