package SteamGame.recommend.domain.game.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GameNameRequestDTO {
    @NotBlank
    private String GameName;
    private int review;
    private Boolean korean_check;
    private Boolean free_check;
}
