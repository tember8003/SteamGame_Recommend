package SteamGame.recommend.domain.game.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
public class GameRequestDTO {
    private String[] tags;
    private int review;
    private Boolean korean_check;
    private Boolean free_check;
    private String[] excludedTag;
}
