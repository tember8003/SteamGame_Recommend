package SteamGame.recommend.domain.library.dto;

import java.util.List;

// 분석 응답에서 analysisId와 profile을 뺀 모양
public record SharedAnalysisResponse(
        boolean limitedData,
        StatsView stats,
        PhraseView ledger,
        VerdictView verdict,
        List<GamerStatView> gamerStats,
        DiagnosisView diagnosis,
        GenreProfileView genreProfile,
        PicksView picks) {
}
