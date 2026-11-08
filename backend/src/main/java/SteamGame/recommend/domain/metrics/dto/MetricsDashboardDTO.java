package SteamGame.recommend.domain.metrics.dto;

import java.util.List;

// 관리자 대시보드 응답, 영역 순서는 설계 문서 3절과 같다
public record MetricsDashboardDTO(
        String range,
        String from,
        String to,
        String timezone,
        Funnels funnels,
        List<Daily> trend,
        Distribution distribution,
        Recommendations recommendations,
        Shares shares) {

    public record Step(String step, long visitors) {
    }

    public record Funnels(List<Step> taste, List<Step> library) {
    }

    public record Daily(String date, long visitors, long testCompletes, long libraryCompletes) {
    }

    public record CodeCount(String code, long count) {
    }

    // libraryRecordedSince 는 판정 기록이 처음 남은 날이고 없으면 null 이다
    public record Distribution(List<CodeCount> features, List<CodeCount> tasteTypes, List<CodeCount> libraryLedger,
                               List<CodeCount> libraryCrime, List<CodeCount> libraryGenre, String libraryRecordedSince) {
    }

    public record ContextMetric(String context, long impressions, long up, long down, long click, double upRate) {
    }

    public record TopGame(long appid, String name, long impressions) {
    }

    public record Recommendations(long totalImpressions, List<ContextMetric> contexts, List<TopGame> topGames) {
    }

    // 클릭이 0 이면 inflowPerClick 은 null 이다
    public record ShareStat(long linkClicks, long imageClicks, long inflows, Double inflowPerClick) {
    }

    public record Shares(ShareStat taste, ShareStat library) {
    }
}
