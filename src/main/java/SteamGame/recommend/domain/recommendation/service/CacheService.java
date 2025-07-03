package SteamGame.recommend.domain.recommendation.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class CacheService {
    private final InMemoryCacheService cacheService;
    private static final Duration RECOMMEND_TTL = Duration.ofMinutes(30);
    private static final Duration TAGS_TTL = Duration.ofHours(6);

    public CacheService(InMemoryCacheService cacheService) {
        this.cacheService = cacheService;
    }

    // 중복 추천 방지
    public boolean isAlreadyRecommended(long appid) {
        return "true".equals(cacheService.get(keyForRecommended(appid)));
    }

    // 게임 추천 설정
    public void setRecommended(long appid) {
        cacheService.set(keyForRecommended(appid), "true", RECOMMEND_TTL);
    }

    private String keyForRecommended(long appid) {
        return "recommended:" + appid;
    }

    public List<String> getCachedTags(String inputHash) {
        String key = "gemini:tag:" + inputHash;
        return cacheService.getList(key);
    }

    public void cacheTags(String inputHash, List<String> tags) {
        String key = "gemini:tag:" + inputHash;
        cacheService.setList(key, tags, TAGS_TTL);
    }
}