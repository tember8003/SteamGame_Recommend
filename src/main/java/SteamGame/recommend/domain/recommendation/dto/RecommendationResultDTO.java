package SteamGame.recommend.domain.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RecommendationResultDTO {
    private List<String> usedTags;
    private List<SteamAppDTO> recommendedGame;
}
