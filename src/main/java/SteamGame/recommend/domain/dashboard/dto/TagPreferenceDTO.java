package SteamGame.recommend.domain.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TagPreferenceDTO {
    private String tagName;
    private int gameCount;
    private BigDecimal totalPlaytime;
    private double preferenceScore; // 0-100
}
