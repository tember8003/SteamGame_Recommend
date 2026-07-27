package SteamGame.recommend.domain.choice.dto;

import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChoiceResultDTO {
    private String sessionId;
    private List<String> usedTags;
    private List<SteamAppDTO> recommendedGames;
    private List<Long> selectedGames;
}
