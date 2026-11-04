package SteamGame.recommend.domain.taste.dto;

import java.util.List;
import java.util.Map;

// v2 결과 응답, v1 응답(TasteResultResponse)에 새 필드가 null로 끼지 않게 따로 둔다
public record TasteV2ResultResponse(
        String version,
        String code,
        Map<String, Map<String, Integer>> axisScores,
        List<AxisDetailView> axisDetails,
        TypeView type) {
}
