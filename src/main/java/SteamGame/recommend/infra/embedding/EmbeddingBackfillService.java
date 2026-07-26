package SteamGame.recommend.infra.embedding;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import SteamGame.recommend.infra.api.GeminiEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * games 임베딩 백필 배치. DetailCrawlerService 의 게이트형 배치 패턴을 따른다.
 * 성공 행은 임베딩이 채워져 큐에서 빠지고, 실패 행은 이번 run 동안 재선택되지 않도록 제외한다.
 * 배치 전체 실패는 주로 Gemini 429(rate limit)이므로 백오프 후 재시도하고,
 * 최대 재시도 후에도 실패하면 API 다운으로 보고 run 을 중단한다.
 */
@Slf4j
@Service
public class EmbeddingBackfillService {

    private final EmbeddingRepository embeddingRepository;
    private final GameRepository gameRepository;
    private final TagRepository tagRepository;
    private final GeminiEmbeddingService geminiEmbeddingService;
    private final EmbeddingProperties props;
    private final JdbcTemplate jdbc;

    public EmbeddingBackfillService(EmbeddingRepository embeddingRepository, GameRepository gameRepository,
                                    TagRepository tagRepository, GeminiEmbeddingService geminiEmbeddingService,
                                    EmbeddingProperties props, JdbcTemplate jdbc) {
        this.embeddingRepository = embeddingRepository;
        this.gameRepository = gameRepository;
        this.tagRepository = tagRepository;
        this.geminiEmbeddingService = geminiEmbeddingService;
        this.props = props;
        this.jdbc = jdbc;
    }

    public record EmbedResult(int processed, int failed) {}

    public EmbedResult runBackfill() {
        Long logId = startCrawlLog();
        Set<Long> failedIds = new HashSet<>();
        int processed = 0, failed = 0;
        boolean apiDown = false;

        while (true) {
            // 실패분을 건너뛰고도 batchSize 만큼 확보되도록 여유 있게 조회 후 필터
            List<Long> batch = embeddingRepository.selectNextToEmbed(props.getBatchSize() + failedIds.size())
                    .stream().filter(id -> !failedIds.contains(id)).limit(props.getBatchSize()).toList();
            if (batch.isEmpty()) {
                break;
            }

            Map<Long, Game> gameByAppid = new HashMap<>();
            for (Game g : gameRepository.findByAppidIn(batch)) {
                gameByAppid.put(g.getAppid(), g);
            }
            Map<Long, List<String>> tagsByAppid = groupTags(batch);

            List<Long> appids = new ArrayList<>();
            List<String> docs = new ArrayList<>();
            for (Long appid : batch) {
                Game g = gameByAppid.get(appid);
                if (g == null) {
                    failedIds.add(appid);
                    failed++;
                    continue;
                }
                String doc = EmbeddingTextBuilder.build(
                        g.getName(), g.getGenres(),
                        tagsByAppid.getOrDefault(appid, List.of()),
                        g.getDescription(), props.getDescriptionMaxChars());
                appids.add(appid);
                docs.add(doc);
            }
            if (docs.isEmpty()) {
                break;
            }

            List<float[]> vectors = embedBatchWithBackoff(docs);
            if (vectors.size() != docs.size()) {
                log.warn("임베딩 배치가 재시도 {}회 후에도 실패 — 백필 중단 (요청 {} 응답 {})",
                        props.getMaxBatchRetries(), docs.size(), vectors.size());
                apiDown = true;
                break;
            }

            for (int i = 0; i < appids.size(); i++) {
                Long appid = appids.get(i);
                float[] vec = vectors.get(i);
                if (vec == null || vec.length == 0) {
                    failedIds.add(appid);
                    failed++;
                    continue;
                }
                try {
                    embeddingRepository.updateEmbedding(appid, vec);
                    processed++;
                } catch (Exception e) {
                    failedIds.add(appid);
                    failed++;
                    log.warn("임베딩 저장 실패 appid {}: {}", appid, e.getMessage());
                }
            }
            sleep(props.getRequestDelayMs());
        }

        String note = "processed=" + processed + " failed=" + failed + (apiDown ? " (api_down)" : "");
        finishCrawlLog(logId, processed, failed, note);
        log.info("embedding backfill done: {}", note);
        return new EmbedResult(processed, failed);
    }

    // findGameTagPairs: Object[]{appid, tagName} → appid 별 태그명 리스트
    private Map<Long, List<String>> groupTags(List<Long> appids) {
        Map<Long, List<String>> map = new HashMap<>();
        for (Object[] row : tagRepository.findGameTagPairs(appids)) {
            Long appid = ((Number) row[0]).longValue();
            String tagName = (String) row[1];
            map.computeIfAbsent(appid, k -> new ArrayList<>()).add(tagName);
        }
        return map;
    }

    // 배치 실패(주로 Gemini 429 rate limit)면 백오프 후 같은 배치를 재시도해 분당 한도가 리셋될 시간을 준다
    private List<float[]> embedBatchWithBackoff(List<String> docs) {
        List<float[]> vectors = geminiEmbeddingService.embedBatch(docs);
        int attempts = 0;
        while (vectors.size() != docs.size() && attempts < props.getMaxBatchRetries()) {
            attempts++;
            log.info("임베딩 배치 실패 (rate limit 추정) — {}s 대기 후 재시도 {}/{}",
                    props.getRetryBackoffMs() / 1000, attempts, props.getMaxBatchRetries());
            sleep(props.getRetryBackoffMs());
            vectors = geminiEmbeddingService.embedBatch(docs);
        }
        return vectors;
    }

    private void sleep(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private Long startCrawlLog() {
        try {
            return jdbc.queryForObject("""
                INSERT INTO crawl_log (started_at, batch_type) VALUES (?, 'embed_backfill')
                RETURNING id""", Long.class, OffsetDateTime.now());
        } catch (Exception e) {
            log.warn("Could not create crawl_log row: {}", e.getMessage());
            return null;
        }
    }

    private void finishCrawlLog(Long id, int processed, int failed, String note) {
        if (id == null) return;
        try {
            jdbc.update("""
                UPDATE crawl_log SET finished_at = ?, processed_count = ?, failed_count = ?, note = ?
                WHERE id = ?""", OffsetDateTime.now(), processed, failed, note, id);
        } catch (Exception e) {
            log.warn("Could not finalize crawl_log row {}: {}", id, e.getMessage());
        }
    }
}
