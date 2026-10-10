package SteamGame.recommend.domain.recommendation.dto;

import SteamGame.recommend.domain.impression.entity.ImpressionSource;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    // 비슷한 게임 추천에서 그 게임을 고른 순위기, 노출 기록에만 쓰고 응답에는 싣지 않는다
    @JsonIgnore
    private ImpressionSource source;
}
