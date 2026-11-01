package SteamGame.recommend.domain.taste.content;

import java.util.List;

// 게이머 성향 유형
public record TasteType(
        String code,
        LocalizedText name,
        LocalizedText work,
        long workAppid,
        LocalizedText oneLiner,
        LocalizedText description,
        LocalizedText traits,
        List<TagWeight> tagWeights,
        List<Long> featuredAppids,
        ShareText share) {

    // 요청마다 같은 목록을 나눠 쓰므로 바꿀 수 없게 복사한다, null은 빈 목록으로 두어 검증기가 개수로 잡는다
    public TasteType {
        tagWeights = tagWeights == null ? List.of() : List.copyOf(tagWeights);
        featuredAppids = featuredAppids == null ? List.of() : List.copyOf(featuredAppids);
    }
}
