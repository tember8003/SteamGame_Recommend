package SteamGame.recommend.domain.taste.content;

import java.util.List;

// 테스트 문항
public record TasteQuestion(int id, String axis, LocalizedText text, List<TasteOption> options) {

    // 요청마다 같은 목록을 나눠 쓰므로 바꿀 수 없게 복사한다, null은 빈 목록으로 두어 검증기가 개수로 잡는다
    public TasteQuestion {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
