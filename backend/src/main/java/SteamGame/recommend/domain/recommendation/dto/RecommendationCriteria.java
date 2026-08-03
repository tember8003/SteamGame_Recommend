package SteamGame.recommend.domain.recommendation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

@Getter
@Builder
public class RecommendationCriteria {
    private Integer players;
    private SessionLength sessionLength;
    private Integer budgetCents;
    private String moodText;
    private List<String> excludeTags;
    private int review;
    private Boolean korean;
    private Boolean free;
    private Set<Long> excludeAppids;
    private boolean nlParseFailed;

    // 2인 이상이면 코옵 하드컷이 필요함
    public boolean needsCoop() { return players != null && players >= 2; }
    // 분위기 텍스트가 있으면 임베딩 경로를 탐
    public boolean hasMood() { return moodText != null && !moodText.isBlank(); }
    // 하드 제약이 하나도 없으면 빈 요청으로 봄
    public boolean isEmpty() {
        return !hasMood() && budgetCents == null && !needsCoop()
                && (excludeTags == null || excludeTags.isEmpty()) && sessionLength == null;
    }
}
