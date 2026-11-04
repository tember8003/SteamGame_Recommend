package SteamGame.recommend.domain.taste.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 취향 테스트 v2 문구 데이터, 축 이름과 16유형은 v1 파일의 것을 그대로 쓴다
// axisSummaries는 축 id마다 보조 설명 키와 문구를 담는다
public record TasteV2Content(
        String version,
        List<TasteV2Question> questions,
        Map<String, Map<String, LocalizedText>> axisSummaries) {

    // 요청마다 같은 객체를 나눠 쓰므로 안쪽 맵까지 바꿀 수 없게 복사한다, null은 빈 값으로 두어 검증기가 잡는다
    public TasteV2Content {
        questions = questions == null ? List.of() : List.copyOf(questions);
        if (axisSummaries == null) {
            axisSummaries = Map.of();
        } else {
            Map<String, Map<String, LocalizedText>> copy = new LinkedHashMap<>();
            axisSummaries.forEach((axis, byKey) -> copy.put(axis, byKey == null ? Map.of() : Map.copyOf(byKey)));
            axisSummaries = Collections.unmodifiableMap(copy);
        }
    }
}
