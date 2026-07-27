package SteamGame.recommend.domain.guess.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewGuessAnswerResponseDTO {
    private boolean correct;
    private boolean gameCompleted;
    private int nextRoundIndex;
    private Long correctAnswerId;  // reveal the answer
    private String correctGameName;
    private int currentScore;
}