package SteamGame.recommend.domain.recommendation.util;

import SteamGame.recommend.domain.recommendation.dto.SessionLength;
import java.math.BigDecimal;

// positive_game_hour 는 총 누적 플레이 시간이라 볼륨 근사임
public final class VolumeBuckets {
    private VolumeBuckets() {}
    private static final BigDecimal SHORT_MAX = new BigDecimal("10");
    private static final BigDecimal LONG_MIN = new BigDecimal("40");
    public static SessionLength classify(BigDecimal hours) {
        if (hours == null) return null;
        if (hours.compareTo(SHORT_MAX) <= 0) return SessionLength.SHORT;
        if (hours.compareTo(LONG_MIN) >= 0) return SessionLength.LONG;
        return SessionLength.MEDIUM;
    }
}
