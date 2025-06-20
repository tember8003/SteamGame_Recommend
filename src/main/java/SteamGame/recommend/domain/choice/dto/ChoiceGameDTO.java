package SteamGame.recommend.domain.choice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChoiceGameDTO {
    private Long appid;
    private String name;
    private String imageUrl;
    private String description;
}