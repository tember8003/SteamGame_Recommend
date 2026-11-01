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
import SteamGame.recommend.domain.taste.content.LocalizedText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 언어 없는 분석 결과를 언어 하나를 골라 응답 모양으로 바꾼다
public final class LibraryViewMapper {

    private static final Logger log = LoggerFactory.getLogger(LibraryViewMapper.class);
    private static final String TOP_GROUP = "topGroup";

    private LibraryViewMapper() {
    }

    public static AnalyzeResponse toAnalyzeResponse(String analysisId, SteamProfile profile, LibraryAnalysis a,
                                                    LibraryContent content, Lang lang) {
        return new AnalyzeResponse(analysisId, a.limitedData(),
                new ProfileView(profile.personaName(), profile.avatarUrl(), profile.steamId64()),
                stats(a.stats()),
                phrase(a.ledger(), content, lang, false),
                verdict(a, content, lang, false),
                gamerStats(a.gamerStats(), content, lang),
                diagnosis(a.diagnosis()));
    }

    // 공유 스냅샷은 문구 파일이 바뀐 뒤에 읽힐 수 있어 현재 문구 파일에서 채울 수 있는 후보를 고른다
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
    // lenient면 번호를 현재 후보 수에 맞춘 자리부터 차례로 보며, 저장된 params로 자리표시자를 모두 채울 수 있는 첫 후보를 쓴다
    // 그런 후보가 없으면 문구 파일의 결함이라 예외를 던진다
    private static PhraseView phrase(Judged j, LibraryContent content, Lang lang, boolean lenient) {
        List<Phrase> candidates = content.phrases().get(j.code());
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("library phrase not found: " + j.code());
        }
        Phrase chosen;
        if (lenient) {
            chosen = firstFillable(j, candidates);
        } else {
            int index = j.phraseIndex();
            if (index < 0 || index >= candidates.size()) {
                throw new IllegalStateException("library phrase not found: " + j.code() + "#" + index);
            }
            chosen = candidates.get(index);
        }
        String text = chosen.text().get(lang);
        Map<String, Object> params = new LinkedHashMap<>(j.params());
        // 분석기는 묶음 키를 넣어 두므로 여기서 언어별 이름으로 바꾼다
        Object group = params.get(TOP_GROUP);
        if (group instanceof String key && !key.isEmpty()) {
            LocalizedText label = content.groupLabels().get(key);
            if (label == null) {
                // 묶음 키가 바뀐 옛 스냅샷, 키를 그대로 보여 주되 눈에 띄게 남긴다
                log.warn("library group label missing, showing the key: code={}, group={}", j.code(), key);
            } else {
                params.put(TOP_GROUP, label.get(lang));
            }
        }
        return new PhraseView(j.code(), text, params);
    }

    private static Phrase firstFillable(Judged j, List<Phrase> candidates) {
        int start = Math.floorMod(j.phraseIndex(), candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            Phrase candidate = candidates.get((start + i) % candidates.size());
            if (j.params().keySet().containsAll(candidate.params())) {
                return candidate;
            }
        }
        throw new IllegalStateException("library phrase has no candidate fillable from stored params: " + j.code());
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
