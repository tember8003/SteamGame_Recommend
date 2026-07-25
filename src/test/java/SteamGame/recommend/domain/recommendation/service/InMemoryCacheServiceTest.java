package SteamGame.recommend.domain.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCacheServiceTest {

    private final InMemoryCacheService cache = new InMemoryCacheService(new ObjectMapper());

    @Test
    void cleanupExpiredCache_removesExpiredEntries() throws InterruptedException {
        cache.set("k", "v", Duration.ofMillis(1));
        Thread.sleep(5);

        cache.cleanupExpiredCache(); // @Scheduled 로 도는 메서드를 직접 호출함

        assertThat(cache.hasKey("k")).isFalse();
    }

    @Test
    void cleanupExpiredCache_keepsLiveEntries() {
        cache.set("k", "v", Duration.ofMinutes(10));

        cache.cleanupExpiredCache();

        assertThat(cache.get("k")).isEqualTo("v");
    }
}
