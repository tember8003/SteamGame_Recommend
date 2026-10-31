package SteamGame.recommend.domain.taste.content;

import java.util.List;
import java.util.Optional;

// 취향 테스트 문구 데이터 전체(문항, 축, 유형)
public record TasteContent(
        String version,
        List<TasteAxis> axes,
        List<TasteQuestion> questions,
        List<TasteType> types) {

    // 코드는 대소문자를 구분하지 않는다
    public Optional<TasteType> type(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return types.stream().filter(t -> t.code().equalsIgnoreCase(code)).findFirst();
    }
}
