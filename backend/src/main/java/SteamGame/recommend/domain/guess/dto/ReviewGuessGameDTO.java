package SteamGame.recommend.domain.guess.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewGuessGameDTO {
    private Long appid;
    private String name;
    private String imageUrl;
    private String description;
}