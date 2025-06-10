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
public class MonthlyActivityDTO {
    private String month; // "2024-01"
    private int gamesPlayed;
    private BigDecimal totalPlaytime;
    private int newGamesPurchased;
}
