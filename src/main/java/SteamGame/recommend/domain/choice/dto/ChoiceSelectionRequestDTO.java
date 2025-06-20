package SteamGame.recommend.domain.choice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChoiceSelectionRequestDTO {
    @NotNull
    private String sessionId;

    @Min(0)
    private int roundIndex;

    @NotNull
    private Long selectedGameId;
}