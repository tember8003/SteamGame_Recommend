package SteamGame.recommend.domain.taste.content;

import java.util.List;

// v2 테스트 문항, 보기는 A, B, C, D 네 개로 둔다
public record TasteV2Question(int id, String axis, LocalizedText text, List<TasteV2Option> options) {

    // 요청마다 같은 목록을 나눠 쓰므로 바꿀 수 없게 복사한다, null은 빈 목록으로 두어 검증기가 개수로 잡는다
    public TasteV2Question {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
