package SteamGame.recommend.domain.library.analysis;

import java.util.List;
import java.util.Map;

// 분석 결과 전체, 문구 원문은 담지 않고 코드와 후보 번호와 채울 값만 담는다
public record LibraryAnalysis(
        boolean limitedData,
        Stats stats,
        Judged ledger,
        Verdict verdict,
        List<GamerStat> gamerStats,
        Diagnosis diagnosis) {

    // 시간은 소수 첫째 자리, 비율은 소수 셋째 자리
    public record Stats(
            int ownedCount,
            double totalHours,
            int zeroPlayCount,
            double zeroPlayRatio,
            TopGame mostPlayed,
            int recentGameCount,
            double recentHours,
            int matchedCount,
            List<TagShare> topTags) {
    }

    public record TopGame(long appid, String name, double hours, double ratio) {
    }

    public record TagShare(String tag, double hours, double ratio) {
    }

    // code는 문구 코드, phraseIndex번째 후보를 쓰고 params는 그 문구가 쓰는 자리표시자 값만 담는다
    public record Judged(String code, int phraseIndex, Map<String, Object> params) {
    }

    public record Verdict(Judged crime, Judged sentence) {
    }

    // value가 null이면 근거 없음
    public record GamerStat(StatCode code, Integer value) {
    }

    public record Diagnosis(String favoriteTag, List<TopGame> concentratedGames, String hiddenTaste) {
    }
}
