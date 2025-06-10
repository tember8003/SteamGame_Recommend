package SteamGame.recommend.domain.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileSummary {
    private int totalGames;
    private BigDecimal totalPlaytime; // 시간 단위
    private int gamesPlayedRecently; // 최근 2주
    private String favoriteGenre;
    private BigDecimal averagePlaytimePerGame;
}
