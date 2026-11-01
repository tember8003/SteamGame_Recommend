package SteamGame.recommend.domain.library.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 장르 결과의 state를 정하는 기준값, 실제 사용자 분포를 본 뒤 고칠 수 있게 내용 파일에 둔다
// 비율은 0에서 1 사이 값이다
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenreRules(
        double minClassifiedHours,
        double minCoverageRatio,
        double mixedGapRatio,
        double secondaryMinRatio) {
}
