package SteamGame.recommend.domain.choice.dto;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChoiceSession implements Serializable {
    private String sessionId;
    private List<ChoiceRoundDTO> rounds;
    private List<Long> selections;
    private boolean completed;
}
