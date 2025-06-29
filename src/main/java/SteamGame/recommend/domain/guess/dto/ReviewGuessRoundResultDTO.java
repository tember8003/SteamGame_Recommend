package SteamGame.recommend.domain.guess.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewGuessRoundResultDTO {
    private int roundIndex;
    private boolean correct;
    private Long selectedGameId;
    private String selectedGameName;
    private Long correctGameId;
    private String correctGameName;
}
