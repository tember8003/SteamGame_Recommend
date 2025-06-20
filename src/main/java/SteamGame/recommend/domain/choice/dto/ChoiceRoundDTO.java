package SteamGame.recommend.domain.choice.dto;

import SteamGame.recommend.domain.recommendation.dto.RecommendationResultDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChoiceRoundDTO {
    private int roundIndex;
    private RecommendationResultDTO game1;
    private RecommendationResultDTO game2;
}