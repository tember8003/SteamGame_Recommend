package SteamGame.recommend.domain.library.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 지금 해볼 게임, 다시 켤 게임, 안 맞는 장르를 고르는 기준값, 실제 분포를 본 뒤 고칠 수 있게 내용 파일에 둔다
// minPositivePct는 0~100 범위다(reviews.positive_percentage와 같은 단위)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PickRules(
        int tasteTagCount,
        int candidateTagCount,
        int minMatchedTags,
        int minReviews,
        double minPositivePct,
        int maxPlayNext,
        double revisitMinHours,
        int revisitIdleDays,
        int maxRevisit,
        int abandonedMinMinutes,
        int abandonedMaxMinutes,
        int abandonedIdleDays,
        int abandonedMinGames) {
}
