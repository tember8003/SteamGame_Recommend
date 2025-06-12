package SteamGame.recommend.domain.recommendation.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class CacheService {
    private final RedisTemplate<String, String> redisTemplate;
    private static final Duration RECOMMEND_TTL = Duration.ofMinutes(30);
    private static final Duration TAGS_TTL      = Duration.ofHours(6);

    public CacheService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //Prevent recommending duplicate games.
    public boolean isAlreadyRecommended(long appid) {
        return "true".equals(redisTemplate.opsForValue().get(keyForRecommended(appid)));
    }

    //Game recommendation setting
    public void setRecommended(long appid) {
        redisTemplate.opsForValue().set(keyForRecommended(appid), "true", RECOMMEND_TTL);
    }

    private String keyForRecommended(long appid) {
        return "recommended:" + appid;
    }

    public List<String> getCachedTags(String inputHash) {
        String key = "gemini:tag:" + inputHash;
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    public void cacheTags(String inputHash, List<String> tags) {
        String key = "gemini:tag:" + inputHash;

        redisTemplate.delete(key);

        redisTemplate.opsForList().rightPushAll(key, tags);
        redisTemplate.expire(key, TAGS_TTL);
    }
}