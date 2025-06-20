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
public class ChoiceResultRequestDTO {
    @NotNull
    private String sessionId;

    @Min(0)
    private int review = 0;
    private Boolean korean_check;
    private Boolean free_check;
}