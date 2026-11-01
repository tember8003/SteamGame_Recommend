package SteamGame.recommend.domain.library.dto;

import java.util.List;

public record StatsView(
        int ownedCount,
        double totalHours,
        int zeroPlayCount,
        double zeroPlayRatio,
        TopGameView mostPlayed,
        int recentGameCount,
        double recentHours,
        int matchedCount,
        List<TagShareView> topTags) {
}
