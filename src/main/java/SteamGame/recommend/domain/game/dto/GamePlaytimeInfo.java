package SteamGame.recommend.domain.game.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GamePlaytimeInfo {
    private Long appId;
    private BigDecimal totalHours;        // Total Playtime
    private BigDecimal recentHours;       // Playtime for the last two weeks
    private LocalDate lastPlayedDate;     // Last Play Date

    public boolean isNeverPlayed() {
        return totalHours.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isShortPlayed() {
        return totalHours.compareTo(BigDecimal.valueOf(5)) <= 0 &&
                totalHours.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isMediumPlayed() {
        return totalHours.compareTo(BigDecimal.valueOf(5)) > 0 &&
                totalHours.compareTo(BigDecimal.valueOf(50)) <= 0;
    }

    public boolean isLongPlayed() {
        return totalHours.compareTo(BigDecimal.valueOf(50)) > 0;
    }
}
