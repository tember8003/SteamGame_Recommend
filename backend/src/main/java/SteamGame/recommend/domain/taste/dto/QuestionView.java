package SteamGame.recommend.domain.taste.dto;

import java.util.List;

// 문항 응답, 축은 일부러 싣지 않는다
public record QuestionView(int id, String text, List<OptionView> options) {
}
