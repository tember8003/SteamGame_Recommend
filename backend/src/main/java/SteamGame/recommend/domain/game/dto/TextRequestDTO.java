package SteamGame.recommend.domain.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class TextRequestDTO {
    @NotBlank
    private String text;
    private int review;
    private Boolean korean_check;
    private Boolean free_check;
    private Set<Long> excludeAppids;

    @Size(max = 64)
    private String clientId;
}
