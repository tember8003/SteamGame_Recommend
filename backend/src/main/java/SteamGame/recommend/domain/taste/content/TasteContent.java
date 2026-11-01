package SteamGame.recommend.domain.taste.content;

import java.util.List;
import java.util.Optional;

// 취향 테스트 문구 데이터 전체(문항, 축, 유형)
public record TasteContent(
        String version,
        List<TasteAxis> axes,
        List<TasteQuestion> questions,
        List<TasteType> types) {

    // 요청마다 같은 목록을 나눠 쓰므로 바꿀 수 없게 복사한다, null은 빈 목록으로 두어 검증기가 개수로 잡는다
    public TasteContent {
        axes = axes == null ? List.of() : List.copyOf(axes);
        questions = questions == null ? List.of() : List.copyOf(questions);
        types = types == null ? List.of() : List.copyOf(types);
    }

    // 코드는 대소문자를 구분하지 않는다
    public Optional<TasteType> type(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return types.stream().filter(t -> t.code().equalsIgnoreCase(code)).findFirst();
    }
}
