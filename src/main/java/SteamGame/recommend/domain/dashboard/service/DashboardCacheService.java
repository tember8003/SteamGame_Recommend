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

    private static final Duration DASHBOARD_TTL = Duration.ofHours(6); // 6시간 캐시
    private static final Duration ERROR_TTL = Duration.ofMinutes(1); // 에러 시 1분 캐시
    private static final String DASHBOARD_KEY_PREFIX = "dashboard:";

    public DashboardCacheService(RedisTemplate<String, String> redisTemplate,
                                 ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 캐시된 대시보드 조회
     */
    public Optional<DashboardDTO> getCachedDashboard(String steamId) {
        try {
            String key = DASHBOARD_KEY_PREFIX + steamId;
            String cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                DashboardDTO dashboard = objectMapper.readValue(cached, DashboardDTO.class);
                log.info("대시보드 캐시 히트: steamId={}", steamId);
                return Optional.of(dashboard);
            }

            log.debug("대시보드 캐시 미스: steamId={}", steamId);
            return Optional.empty();

        } catch (JsonProcessingException e) {
            log.error("대시보드 캐시 읽기 실패: steamId={}", steamId, e);
            // 캐시 오류 시 해당 키 삭제
            invalidateDashboard(steamId);
            return Optional.empty();
        }
    }

    /**
     * 대시보드 캐시 저장 (기본 TTL)
     */
    public void cacheDashboard(String steamId, DashboardDTO dashboard) {
        cacheDashboardWithTTL(steamId, dashboard, DASHBOARD_TTL.getSeconds());
    }

    /**
     * 🔧 커스텀 TTL로 대시보드 캐시 저장
     */
    public void cacheDashboardWithTTL(String steamId, DashboardDTO dashboard, long ttlSeconds) {
        try {
            String key = DASHBOARD_KEY_PREFIX + steamId;
            String json = objectMapper.writeValueAsString(dashboard);

            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
            log.info("대시보드 캐시 저장: steamId={}, TTL={}초", steamId, ttlSeconds);

        } catch (JsonProcessingException e) {
            log.error("대시보드 캐시 저장 실패: steamId={}", steamId, e);
        }
    }

    /**
     * 특정 사용자 대시보드 캐시 무효화
     */
    public void invalidateDashboard(String steamId) {
        String key = DASHBOARD_KEY_PREFIX + steamId;
        Boolean deleted = redisTemplate.delete(key);
        log.info("대시보드 캐시 무효화: steamId={}, 삭제됨={}", steamId, deleted);
    }

    /**
     * 캐시 유효성 확인
     */
    public boolean isCacheValid(String steamId) {
        String key = DASHBOARD_KEY_PREFIX + steamId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 🔧 캐시 만료 시간 조회 (초 단위)
     */
    public long getCacheExpiration(String steamId) {
        String key = DASHBOARD_KEY_PREFIX + steamId;
        Long expiration = redisTemplate.getExpire(key);
        return expiration != null ? expiration : -1;
    }

    /**
     * 🔧 모든 대시보드 캐시 키 조회 (관리용)
     */
    public Set<String> getAllCacheKeys() {
        return redisTemplate.keys(DASHBOARD_KEY_PREFIX + "*");
    }

    /**
     * 🔧 모든 대시보드 캐시 삭제 (관리용)
     */
    public long clearAllDashboardCache() {
        Set<String> keys = getAllCacheKeys();
        if (keys.isEmpty()) {
            log.info("삭제할 대시보드 캐시가 없습니다");
            return 0;
        }

        Long deletedCount = redisTemplate.delete(keys);
        log.info("모든 대시보드 캐시 삭제: {}개", deletedCount);
        return deletedCount != null ? deletedCount : 0;
    }

    public CacheStats getCacheStats() {
        Set<String> allKeys = getAllCacheKeys();
        long totalCached = allKeys.size();

        long validCaches = allKeys.stream()
                .mapToLong(key -> {
                    Long expiration = redisTemplate.getExpire(key);
                    return (expiration != null && expiration > 0) ? 1 : 0;
                })
                .sum();

        return new CacheStats(totalCached, validCaches, totalCached - validCaches);
    }

    /**
     * 🔧 캐시 통계 DTO
     */
    public static class CacheStats {
        private final long totalCached;
        private final long validCaches;
        private final long expiredCaches;

        public CacheStats(long totalCached, long validCaches, long expiredCaches) {
            this.totalCached = totalCached;
            this.validCaches = validCaches;
            this.expiredCaches = expiredCaches;
        }

        public long getTotalCached() { return totalCached; }
        public long getValidCaches() { return validCaches; }
        public long getExpiredCaches() { return expiredCaches; }

        @Override
        public String toString() {
            return String.format("CacheStats{total=%d, valid=%d, expired=%d}",
                    totalCached, validCaches, expiredCaches);
        }
    }
}