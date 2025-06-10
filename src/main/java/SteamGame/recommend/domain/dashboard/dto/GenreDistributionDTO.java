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
public class GenreDistributionDTO {
    private String genre;
    private int gameCount;
    private BigDecimal playtimeHours;
    private double percentage;
    private String color; // 차트용 색상
}
