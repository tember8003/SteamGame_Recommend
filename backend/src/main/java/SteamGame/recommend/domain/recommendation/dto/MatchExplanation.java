package SteamGame.recommend.domain.recommendation.dto;

import java.util.List;

// 후보 게임 한 건에 대한 사유 목록과 주의점 목록을 담음
public record MatchExplanation(List<ExplanationItem> reasons, List<ExplanationItem> caveats) {
}
