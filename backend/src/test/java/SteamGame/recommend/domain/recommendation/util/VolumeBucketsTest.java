package SteamGame.recommend.domain.recommendation.util;

import SteamGame.recommend.domain.recommendation.dto.SessionLength;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class VolumeBucketsTest {
    @Test void classifiesBoundaries() {
        assertThat(VolumeBuckets.classify(new BigDecimal("10.00"))).isEqualTo(SessionLength.SHORT);
        assertThat(VolumeBuckets.classify(new BigDecimal("10.01"))).isEqualTo(SessionLength.MEDIUM);
        assertThat(VolumeBuckets.classify(new BigDecimal("39.99"))).isEqualTo(SessionLength.MEDIUM);
        assertThat(VolumeBuckets.classify(new BigDecimal("40.00"))).isEqualTo(SessionLength.LONG);
        assertThat(VolumeBuckets.classify(null)).isNull();
    }
}
