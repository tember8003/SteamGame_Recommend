package SteamGame.recommend.domain.taste.service;

import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;

import java.util.List;

// games와 explanations는 같은 순서다
public record TasteRecommendResult(List<SteamAppDTO> games, List<GameExplanation> explanations) {
}
