package SteamGame.recommend.domain.recommendation.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class CacheService {
    private final InMemoryCacheService cacheService;
    private static final Duration TAGS_TTL = Duration.ofHours(6);
    private static final Duration EMBED_TTL = Duration.ofHours(6);

    public CacheService(InMemoryCacheService cacheService) {
        this.cacheService = cacheService;
    }

    // 입력 해시 기준 Gemini 태그 캐시(결정적, 전역간섭 없음)
    public List<String> getCachedTags(String inputHash) {
        String key = "gemini:tag:" + inputHash;
        return cacheService.getList(key);
    }

    public void cacheTags(String inputHash, List<String> tags) {
        String key = "gemini:tag:" + inputHash;
        cacheService.setList(key, tags, TAGS_TTL);
    }

    // 입력 해시 기준 임베딩 pgvector 리터럴 캐시(재추천 시 Gemini 재호출 방지)
    public String getCachedEmbedding(String inputHash) {
        return cacheService.get("gemini:embed:" + inputHash);
    }

    public void cacheEmbedding(String inputHash, String literal) {
        cacheService.set("gemini:embed:" + inputHash, literal, EMBED_TTL);
    }
}
