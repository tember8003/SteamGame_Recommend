package SteamGame.recommend.domain.library.dto;

import java.util.List;

public record DiagnosisView(String favoriteTag, List<TopGameView> concentratedGames, String hiddenTaste) {
}
