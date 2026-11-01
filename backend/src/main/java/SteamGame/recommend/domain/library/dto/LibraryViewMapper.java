package SteamGame.recommend.domain.library.dto;

import SteamGame.recommend.domain.library.analysis.LibraryAnalysis;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Diagnosis;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.GamerStat;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Judged;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Stats;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.TopGame;
import SteamGame.recommend.domain.library.content.LibraryContent;
import SteamGame.recommend.domain.library.content.Phrase;
import SteamGame.recommend.domain.library.steam.SteamProfile;
import SteamGame.recommend.domain.taste.content.Lang;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 언어 없는 분석 결과를 언어 하나를 골라 응답 모양으로 바꾼다
public final class LibraryViewMapper {

    private static final String TOP_GROUP = "topGroup";

    private LibraryViewMapper() {
    }

    public static AnalyzeResponse toAnalyzeResponse(String analysisId, SteamProfile profile, LibraryAnalysis a,
                                                    LibraryContent content, Lang lang) {
        return new AnalyzeResponse(analysisId, a.limitedData(),
                new ProfileView(profile.personaName(), profile.avatarUrl()),
                stats(a.stats()),
                phrase(a.ledger(), content, lang, false),
                verdict(a, content, lang, false),
                gamerStats(a.gamerStats(), content, lang),
                diagnosis(a.diagnosis()));
    }

    // 공유 스냅샷은 문구 파일이 바뀐 뒤에 읽힐 수 있어 후보 번호를 현재 후보 수에 맞춰 돌린다
    public static SharedAnalysisResponse toSharedResponse(LibraryAnalysis a, LibraryContent content, Lang lang) {
        return new SharedAnalysisResponse(a.limitedData(),
                stats(a.stats()),
                phrase(a.ledger(), content, lang, true),
                verdict(a, content, lang, true),
                gamerStats(a.gamerStats(), content, lang),
                diagnosis(a.diagnosis()));
    }

    private static VerdictView verdict(LibraryAnalysis a, LibraryContent content, Lang lang, boolean lenient) {
        return new VerdictView(phrase(a.verdict().crime(), content, lang, lenient),
                phrase(a.verdict().sentence(), content, lang, lenient));
    }

    // lenient가 아니면 후보 번호가 범위를 벗어났을 때 틀린 문구를 내지 않고 예외를 던진다
    private static PhraseView phrase(Judged j, LibraryContent content, Lang lang, boolean lenient) {
        List<Phrase> candidates = content.phrases().get(j.code());
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("library phrase not found: " + j.code());
        }
        int index = j.phraseIndex();
        if (lenient) {
            index = Math.floorMod(index, candidates.size());
        } else if (index < 0 || index >= candidates.size()) {
            throw new IllegalStateException("library phrase not found: " + j.code() + "#" + index);
        }
        String text = candidates.get(index).text().get(lang);
        Map<String, Object> params = new LinkedHashMap<>(j.params());
        // 분석기는 묶음 키를 넣어 두므로 여기서 언어별 이름으로 바꾼다
        Object group = params.get(TOP_GROUP);
        if (group instanceof String key && content.groupLabels().containsKey(key)) {
            params.put(TOP_GROUP, content.groupLabels().get(key).get(lang));
        }
        return new PhraseView(j.code(), text, params);
    }

    private static List<GamerStatView> gamerStats(List<GamerStat> stats, LibraryContent content, Lang lang) {
        return stats.stream().map(s -> {
            List<Phrase> labels = content.phrases().get("STAT_" + s.code().name());
            if (labels == null || labels.isEmpty()) {
                throw new IllegalStateException("library stat label not found: " + s.code());
            }
            return new GamerStatView(s.code().name(), s.value(), labels.get(0).text().get(lang));
        }).toList();
    }

    private static StatsView stats(Stats s) {
        return new StatsView(s.ownedCount(), s.totalHours(), s.zeroPlayCount(), s.zeroPlayRatio(),
                s.mostPlayed() == null ? null : topGame(s.mostPlayed()),
                s.recentGameCount(), s.recentHours(), s.matchedCount(),
                s.topTags().stream().map(t -> new TagShareView(t.tag(), t.hours(), t.ratio())).toList());
    }

    private static DiagnosisView diagnosis(Diagnosis d) {
        return new DiagnosisView(d.favoriteTag(),
                d.concentratedGames().stream().map(LibraryViewMapper::topGame).toList(),
                d.hiddenTaste());
    }

    private static TopGameView topGame(TopGame g) {
        return new TopGameView(g.appid(), g.name(), g.hours(), g.ratio());
    }
}
