package SteamGame.recommend.domain.taste.content;

// 성향 축(예: E 탐험 / G 성장)
public record TasteAxis(String id, AxisSide left, AxisSide right) {
}
