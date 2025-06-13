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
public class Recent2WeeksActivityDTO {
    private BigDecimal totalPlaytime;
    private int activeGamesCount;
    private BigDecimal averageSessionTime;
    private GameStatsDTO mostPlayedGame;
}
