package SteamGame.recommend.domain.metrics.dto;

import SteamGame.recommend.domain.feedback.entity.FeedbackContext;

import java.util.List;

public record MetricsOverviewDTO(
        long totalImpressions,
        List<ContextMetric> contexts,
        List<TopGame> topGames
) {
    public record ContextMetric(
            FeedbackContext context,
            long impressions,
            long up,
            long down,
            long click,
            double upRate
    ) {}

    public record TopGame(
            long appid,
            String name,
            long impressions
    ) {}
}
