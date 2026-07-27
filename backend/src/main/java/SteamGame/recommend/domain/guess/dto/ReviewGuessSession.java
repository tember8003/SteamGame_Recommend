package SteamGame.recommend.domain.guess.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewGuessSession implements Serializable {
    private String sessionId;
    private List<ReviewGuessRoundDTO> rounds;
    private List<Long> userAnswers;  // Game IDs selected by the user
    private boolean completed;
    private int currentScore;
}