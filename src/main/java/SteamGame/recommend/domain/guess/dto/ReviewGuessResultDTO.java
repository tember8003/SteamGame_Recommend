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
public class ReviewGuessResultDTO {
    private String sessionId;
    private int correctAnswers;
    private int totalRounds;
    private double accuracy;
    private List<ReviewGuessRoundResultDTO> roundResults;
    private String grade;  // A, B, C, D grade
}