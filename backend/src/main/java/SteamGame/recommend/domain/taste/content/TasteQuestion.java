package SteamGame.recommend.domain.taste.content;

import java.util.List;

// 테스트 문항
public record TasteQuestion(int id, String axis, LocalizedText text, List<TasteOption> options) {
}
