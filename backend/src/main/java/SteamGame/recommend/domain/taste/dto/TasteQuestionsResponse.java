package SteamGame.recommend.domain.taste.dto;

import java.util.List;

public record TasteQuestionsResponse(String version, List<QuestionView> questions) {
}
