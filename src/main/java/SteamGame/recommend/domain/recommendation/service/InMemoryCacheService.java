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

        // Expired Cache Cleanup Scheduler (every 5 minutes)
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cleanupExpiredCache();
            }
        }, 300000, 300000); // 5분마다 실행
    }

    // Save/Enquire String
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

    // Save/Enquire Objects (JSON Serialization)
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
            cache.remove(key); // Remove corrupted cache
            return Optional.empty();
        }
    }

    // Save/Changing List
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

    // Check key presence
    public boolean hasKey(String key) {
        CacheItem item = cache.get(key);
        if (item != null && item.isExpired()) {
            cache.remove(key);
            return false;
        }
        return item != null;
    }

    // delete cache
    public boolean delete(String key) {
        boolean existed = cache.containsKey(key);
        cache.remove(key);
        log.debug("Cache delete: key={}, existed={}", key, existed);
        return existed;
    }

    // Expiration Time Inquiry (seconds)
    public long getExpire(String key) {
        CacheItem item = cache.get(key);
        if (item == null || item.getExpireTime() == null) {
            return -1;
        }

        long secondsUntilExpiry = java.time.Duration.between(LocalDateTime.now(), item.getExpireTime()).getSeconds();
        return Math.max(secondsUntilExpiry, -1);
    }

    // Expired Cache Cleanup
    private void cleanupExpiredCache() {
        int beforeSize = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int afterSize = cache.size();

        if (beforeSize != afterSize) {
            log.debug("Cache cleanup completed: removed {} items, remaining {}",
                    beforeSize - afterSize, afterSize);
        }
    }

    // Cache Statistics
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalKeys", cache.size());
        stats.put("stringKeys", cache.values().stream().mapToInt(item -> item.getType() == CacheType.STRING ? 1 : 0).sum());
        stats.put("objectKeys", cache.values().stream().mapToInt(item -> item.getType() == CacheType.OBJECT ? 1 : 0).sum());
        stats.put("listKeys", cache.values().stream().mapToInt(item -> item.getType() == CacheType.LIST ? 1 : 0).sum());
        return stats;
    }

    // Cache Item Class
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