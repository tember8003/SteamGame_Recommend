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
    private int neverPlayed; // 0시간 게임
    private int shortPlayed; // 1-5시간
    private int mediumPlayed; // 5-50시간
    private int longPlayed; // 50시간+
    private List<GameStatsDTO> topPlayedGames;
    private List<GameStatsDTO> recentlyPlayed;
}
