package SteamGame.recommend.domain.guess.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewGuessStatusDTO {
    private String sessionId;
    private int currentRound;
    private int totalRounds;
    private boolean completed;
    private int currentScore;
}