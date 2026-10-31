package SteamGame.recommend.domain.taste.content;

import java.util.List;

// 게이머 성향 유형
public record TasteType(
        String code,
        LocalizedText name,
        LocalizedText oneLiner,
        LocalizedText description,
        LocalizedText traits,
        List<TagWeight> tagWeights,
        List<Long> featuredAppids,
        ShareText share) {
}
