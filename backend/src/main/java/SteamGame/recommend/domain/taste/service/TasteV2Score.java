package SteamGame.recommend.domain.taste.service;

import java.util.List;
import java.util.Map;

// axisScores는 축 id 순서를 지키는 맵이며 값은 {글자: 점수}, axisDetails도 축 순서를 따름
public record TasteV2Score(String code, Map<String, Map<String, Integer>> axisScores, List<AxisDetail> axisDetails) {
}
