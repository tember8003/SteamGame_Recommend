package SteamGame.recommend.domain.recommendation.dto;

import java.util.List;

// 매칭 오케스트레이션의 최종 산출물, 폴백 사다리를 다 거쳐도 need 를 못 채우면 shortfall 이 참임
public record MatchResultDTO(RecommendationCriteria criteria,
                              List<SteamAppDTO> games,
                              List<ReviewResponseDTO> reviews,
                              List<MatchExplanation> explanations,
                              boolean shortfall) {
}
