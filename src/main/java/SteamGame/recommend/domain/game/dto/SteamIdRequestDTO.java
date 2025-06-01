package SteamGame.recommend.domain.game.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SteamIdRequestDTO {
    @NotBlank
    private String steamId;
    private int review;
    private Boolean korean_check;
    private Boolean free_check;

}
