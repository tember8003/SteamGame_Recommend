package SteamGame.recommend.domain.taste.dto;

import java.util.Map;

public record TasteResultResponse(String code, Map<String, Map<String, Integer>> axisScores, TypeView type) {
}
