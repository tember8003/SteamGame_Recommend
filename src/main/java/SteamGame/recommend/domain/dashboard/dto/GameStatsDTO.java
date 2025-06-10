package SteamGame.recommend.domain.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameStatsDTO {
    private Long appid;
    private String name;
    private String headerImage;
    private BigDecimal playtimeHours;
    private LocalDate lastPlayed;
    private List<String> tags;
}
