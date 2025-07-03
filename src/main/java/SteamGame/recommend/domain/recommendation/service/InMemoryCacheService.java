package SteamGame.recommend.domain.recommendation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class InMemoryCacheService {

    private final Map<String, CacheItem> cache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public InMemoryCacheService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        // 만료된 캐시 정리 스케줄러 (5분마다)
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cleanupExpiredCache();
            }
        }, 300000, 300000); // 5분마다 실행
    }

    // 문자열 저장/조회
    public void set(String key, String value, Duration ttl) {
        LocalDateTime expireTime = ttl != null ? LocalDateTime.now().plus(ttl) : null;
        cache.put(key, new CacheItem(value, expireTime, CacheType.STRING));
        log.debug("Cache set: key={}, ttl={}", key, ttl);
    }

    public String get(String key) {
        CacheItem item = cache.get(key);
        if (item == null || item.isExpired()) {
            if (item != null && item.isExpired()) {
                cache.remove(key);
                log.debug("Cache expired and removed: key={}", key);
            }
            return null;
        }
        return (String) item.getValue();
    }

    // 객체 저장/조회 (JSON 직렬화)
    public <T> void setObject(String key, T object, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(object);
            LocalDateTime expireTime = ttl != null ? LocalDateTime.now().plus(ttl) : null;
            cache.put(key, new CacheItem(json, expireTime, CacheType.OBJECT));
            log.debug("Cache setObject: key={}, type={}, ttl={}", key, object.getClass().getSimpleName(), ttl);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object for cache: key={}", key, e);
        }
    }

    public <T> Optional<T> getObject(String key, Class<T> type) {
        CacheItem item = cache.get(key);
        if (item == null || item.isExpired()) {
            if (item != null && item.isExpired()) {
                cache.remove(key);
            }
            return Optional.empty();
        }

        try {
            T object = objectMapper.readValue((String) item.getValue(), type);
            return Optional.of(object);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize object from cache: key={}, type={}", key, type.getSimpleName(), e);
            cache.remove(key); // 손상된 캐시 제거
            return Optional.empty();
        }
    }

    // 리스트 저장/조회
    public void setList(String key, List<String> list, Duration ttl) {
        LocalDateTime expireTime = ttl != null ? LocalDateTime.now().plus(ttl) : null;
        cache.put(key, new CacheItem(new ArrayList<>(list), expireTime, CacheType.LIST));
        log.debug("Cache setList: key={}, size={}, ttl={}", key, list.size(), ttl);
    }

    @SuppressWarnings("unchecked")
    public List<String> getList(String key) {
        CacheItem item = cache.get(key);
        if (item == null || item.isExpired()) {
            if (item != null && item.isExpired()) {
                cache.remove(key);
            }
            return new ArrayList<>();
        }
        return new ArrayList<>((List<String>) item.getValue());
    }

    // 키 존재 여부 확인
    public boolean hasKey(String key) {
        CacheItem item = cache.get(key);
        if (item != null && item.isExpired()) {
            cache.remove(key);
            return false;
        }
        return item != null;
    }

    // 캐시 삭제
    public boolean delete(String key) {
        boolean existed = cache.containsKey(key);
        cache.remove(key);
        log.debug("Cache delete: key={}, existed={}", key, existed);
        return existed;
    }

    // 만료 시간 조회 (초)
    public long getExpire(String key) {
        CacheItem item = cache.get(key);
        if (item == null || item.getExpireTime() == null) {
            return -1;
        }

        long secondsUntilExpiry = java.time.Duration.between(LocalDateTime.now(), item.getExpireTime()).getSeconds();
        return Math.max(secondsUntilExpiry, -1);
    }

    // 만료된 캐시 정리
    private void cleanupExpiredCache() {
        int beforeSize = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int afterSize = cache.size();

        if (beforeSize != afterSize) {
            log.debug("Cache cleanup completed: removed {} items, remaining {}",
                    beforeSize - afterSize, afterSize);
        }
    }

    // 캐시 통계
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalKeys", cache.size());
        stats.put("stringKeys", cache.values().stream().mapToInt(item -> item.getType() == CacheType.STRING ? 1 : 0).sum());
        stats.put("objectKeys", cache.values().stream().mapToInt(item -> item.getType() == CacheType.OBJECT ? 1 : 0).sum());
        stats.put("listKeys", cache.values().stream().mapToInt(item -> item.getType() == CacheType.LIST ? 1 : 0).sum());
        return stats;
    }

    // 캐시 아이템 클래스
    private static class CacheItem {
        private final Object value;
        private final LocalDateTime expireTime;
        private final CacheType type;

        public CacheItem(Object value, LocalDateTime expireTime, CacheType type) {
            this.value = value;
            this.expireTime = expireTime;
            this.type = type;
        }

        public Object getValue() { return value; }
        public LocalDateTime getExpireTime() { return expireTime; }
        public CacheType getType() { return type; }

        public boolean isExpired() {
            return expireTime != null && LocalDateTime.now().isAfter(expireTime);
        }
    }

    private enum CacheType {
        STRING, OBJECT, LIST
    }
}