package SteamGame.recommend.domain.game.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStatsInfo {
    private String playerName;
    private Long accountCreated;    // Unix timestamp
}