package SteamGame.recommend.domain.taste.content;

import java.util.Map;

// v2 문항 선택지, scores는 그 문항 축의 두 글자에 주는 점수를 담음
public record TasteV2Option(String key, Map<String, Integer> scores, LocalizedText text) {

    // 요청마다 같은 맵을 나눠 쓰므로 바꿀 수 없게 복사한다, null은 빈 맵으로 두어 검증기가 잡는다
    public TasteV2Option {
        scores = scores == null ? Map.of() : Map.copyOf(scores);
    }
}
