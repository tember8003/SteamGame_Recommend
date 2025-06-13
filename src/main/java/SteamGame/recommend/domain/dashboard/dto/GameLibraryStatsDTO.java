package SteamGame.recommend.domain.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameLibraryStatsDTO {
    private int neverPlayed;
    private int shortPlayed; // 1-5 hour
    private int mediumPlayed; // 5-50 hour
    private int longPlayed; // 50 hour +
    private List<GameStatsDTO> topPlayedGames;
    private List<GameStatsDTO> recentlyPlayed;
}
