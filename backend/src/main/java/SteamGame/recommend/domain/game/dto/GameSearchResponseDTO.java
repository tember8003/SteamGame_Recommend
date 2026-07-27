package SteamGame.recommend.domain.game.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameSearchResponseDTO {
    private String gameName;
    private String headerImage;
}
