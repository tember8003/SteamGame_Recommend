package SteamGame.recommend.domain.guess.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewGuessRoundDTO {
    private int roundIndex;
    private String positiveReview;
    private String negativeReview;
    private List<ReviewGuessGameDTO> gameOptions;  // 3 options (answer and 2 not correct)
    private Long correctAnswerId;   // Answer Game ID (Hidden by Client)
}