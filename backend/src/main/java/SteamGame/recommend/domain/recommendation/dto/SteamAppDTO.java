package SteamGame.recommend.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SteamAppDTO {
    private Long appid;
    private String name;
    private String shortDescription;
    private String headerImage;
    private String steamStore;
}
