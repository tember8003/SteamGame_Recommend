package SteamGame.recommend.domain.choice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChoiceStatusDTO {
    private String sessionId;
    private int currentRound;
    private int totalRounds;
    private boolean completed;
}
