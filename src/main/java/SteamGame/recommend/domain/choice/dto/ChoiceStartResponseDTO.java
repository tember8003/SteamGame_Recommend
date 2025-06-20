package SteamGame.recommend.domain.choice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChoiceStartResponseDTO {
    private String sessionId;
    private List<ChoiceRoundDTO> rounds;
}
