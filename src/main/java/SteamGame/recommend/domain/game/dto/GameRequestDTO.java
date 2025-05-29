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
    /* TODO: 나중에 고려할 사항 (태그 제외하기)
    private Boolean excluded_check;
    private String[] excludedTag;
     */
}
