package SteamGame.recommend.domain.taste.service;

import java.util.Map;

// axisScores는 축 id 순서를 지키는 맵이며, 값은 {옆 글자: 점수}
public record TasteScore(String code, Map<String, Map<String, Integer>> axisScores) {
}
