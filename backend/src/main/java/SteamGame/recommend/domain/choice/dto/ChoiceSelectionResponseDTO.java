package SteamGame.recommend.domain.choice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChoiceSelectionResponseDTO {
    private boolean success;
    private boolean completed;
    private int nextRoundIndex;
}
