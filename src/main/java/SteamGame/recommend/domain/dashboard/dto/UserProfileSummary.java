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
    private BigDecimal totalPlaytime;
    private int gamesPlayedRecently; // recent 2 weeks
    private String favoriteGenre;
    private BigDecimal averagePlaytimePerGame;
}
