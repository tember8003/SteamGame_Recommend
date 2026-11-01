package SteamGame.recommend.domain.library.dto;

import java.util.List;

public record AnalyzeResponse(
        String analysisId,
        boolean limitedData,
        ProfileView profile,
        StatsView stats,
        PhraseView ledger,
        VerdictView verdict,
        List<GamerStatView> gamerStats,
        DiagnosisView diagnosis,
        GenreProfileView genreProfile) {
}
