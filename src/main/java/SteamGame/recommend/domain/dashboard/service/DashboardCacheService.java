package SteamGame.recommend.domain.dashboard.service;

import SteamGame.recommend.domain.dashboard.dto.DashboardDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class DashboardCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration DASHBOARD_TTL = Duration.ofHours(6); // Caching for 6 hour
    private static final Duration ERROR_TTL = Duration.ofMinutes(1); // 1 minute cache on error
    private static final String DASHBOARD_KEY_PREFIX = "dashboard:";

    public DashboardCacheService(RedisTemplate<String, String> redisTemplate,
                                 ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // Retrieve cached dashboard
    public Optional<DashboardDTO> getCachedDashboard(String steamId) {
        try {
            String key = DASHBOARD_KEY_PREFIX + steamId;
            String cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                DashboardDTO dashboard = objectMapper.readValue(cached, DashboardDTO.class);
                log.info("Dashboard Cache hit: steamId={}", steamId);
                return Optional.of(dashboard);
            }

            log.debug("Dashboard Cache miss: steamId={}", steamId);
            return Optional.empty();

        } catch (JsonProcessingException e) {
            log.error("Dashboard Cache read fail: steamId={}", steamId, e);
            // Delete the cache key if an error occurs
            invalidateDashboard(steamId);
            return Optional.empty();
        }
    }

    //Store dashboard in cache (with default TTL)
    public void cacheDashboard(String steamId, DashboardDTO dashboard) {
        cacheDashboardWithTTL(steamId, dashboard, DASHBOARD_TTL.getSeconds());
    }

    //Store dashboard in cache with custom TTL
    public void cacheDashboardWithTTL(String steamId, DashboardDTO dashboard, long ttlSeconds) {
        try {
            String key = DASHBOARD_KEY_PREFIX + steamId;
            String json = objectMapper.writeValueAsString(dashboard);

            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
            log.info("Store dashboard in cache: steamId={}, TTL={} second", steamId, ttlSeconds);

        } catch (JsonProcessingException e) {
            log.error("Failed to store dashboard in cache: steamId={}", steamId, e);
        }
    }

    // Invalidate cache for a specific user's dashboard
    public void invalidateDashboard(String steamId) {
        String key = DASHBOARD_KEY_PREFIX + steamId;
        Boolean deleted = redisTemplate.delete(key);
        log.info("Invalidate dashboard cache: steamId={}, deleted={}", steamId, deleted);
    }
    
    public boolean isCacheValid(String steamId) {
        String key = DASHBOARD_KEY_PREFIX + steamId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // Check cache validity
    public long getCacheExpiration(String steamId) {
        String key = DASHBOARD_KEY_PREFIX + steamId;
        Long expiration = redisTemplate.getExpire(key);
        return expiration != null ? expiration : -1;
    }
}