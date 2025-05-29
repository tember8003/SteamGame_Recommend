package SteamGame.recommend.domain.game.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TextRequestDTO {
    @NotBlank
    private String text;
}
