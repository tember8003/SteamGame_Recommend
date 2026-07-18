package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.GameSeed;
import SteamGame.recommend.infra.crawler.dto.ReviewSeed;
import SteamGame.recommend.infra.crawler.dto.SteamSpyAllEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SeedCrawlerService {

    private final SteamSpyClient client;
    private final SteamSpyParser parser;
    private final CatalogUpsertRepository upsert;
    private final JdbcTemplate jdbc;
    private final CrawlerProperties props;

    public SeedCrawlerService(SteamSpyClient client, SteamSpyParser parser,
                              CatalogUpsertRepository upsert, JdbcTemplate jdbc,
                              CrawlerProperties props) {
        this.client = client;
        this.parser = parser;
        this.upsert = upsert;
        this.jdbc = jdbc;
        this.props = props;
    }

    public record SeedResult(int pagesFetched, int gamesUpserted, boolean cappedByMaxPages) {}

    public SeedResult runSeed() {
        Long logId = startCrawlLog();
        int pagesFetched = 0;
        int gamesUpserted = 0;
        int failed = 0;
        boolean capped = false;

        try {
            for (int page = 0; page < props.getMaxPages(); page++) {
                String body = client.fetchAllPage(page);
                List<SteamSpyAllEntry> entries = parser.parseAllPage(body);
                pagesFetched++;

                if (entries.isEmpty()) {
                    log.info("SteamSpy seed: page {} empty, catalog complete", page);
                    break;
                }

                List<GameSeed> gameSeeds = new ArrayList<>();
                List<ReviewSeed> reviewSeeds = new ArrayList<>();
                for (SteamSpyAllEntry e : entries) {
                    int reviewCount = e.getPositive() + e.getNegative();
                    if (reviewCount < props.getMinReviews()) continue;
                    if (e.getName() == null || e.getName().isBlank()) {
                        log.warn("SteamSpy seed: skipping appid {} with blank name", e.getAppid());
                        continue;
                    }
                    gameSeeds.add(new GameSeed(e.getAppid(), truncate(e.getName(), 255),
                            truncate(e.getDeveloper(), 255), truncate(e.getPublisher(), 255),
                            parseOwnersLowerBound(e.getOwners()), reviewCount));
                    reviewSeeds.add(new ReviewSeed(e.getAppid(), reviewCount,
                            positivePercentage(e.getPositive(), reviewCount)));
                }

                if (!gameSeeds.isEmpty()) {
                    upsert.upsertGames(gameSeeds);
                    upsert.upsertReviews(reviewSeeds);
                    gamesUpserted += gameSeeds.size();
                }

                log.info("SteamSpy seed: page {} → {} qualifying (>= {} reviews)",
                        page, gameSeeds.size(), props.getMinReviews());

                if (page == props.getMaxPages() - 1) {
                    capped = true;
                    log.warn("SteamSpy seed: hit maxPages={} — catalog may be truncated. "
                            + "Increase app.crawler.max-pages to cover more.", props.getMaxPages());
                }

                if (!capped) {
                    sleep(props.getPageDelayMs());
                }
            }
        } catch (Exception ex) {
            failed++;
            log.error("SteamSpy seed failed mid-run", ex);
            finishCrawlLog(logId, gamesUpserted, failed, "FAILED: " + ex.getMessage());
            throw ex;
        }

        finishCrawlLog(logId, gamesUpserted, failed,
                capped ? "capped at maxPages=" + props.getMaxPages() : "complete");
        return new SeedResult(pagesFetched, gamesUpserted, capped);
    }

    // "100,000,000 .. 200,000,000" → 100000000L(하한), 파싱 실패 시 null
    static Long parseOwnersLowerBound(String owners) {
        if (owners == null || owners.isBlank()) return null;
        String lower = owners.split("\\.\\.")[0].replaceAll("[^0-9]", "");
        if (lower.isBlank()) return null;
        try { return Long.parseLong(lower); } catch (NumberFormatException e) { return null; }
    }

    static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    static BigDecimal positivePercentage(int positive, int total) {
        if (total <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(positive)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private void sleep(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private Long startCrawlLog() {
        try {
            return jdbc.queryForObject("""
                INSERT INTO crawl_log (started_at, batch_type) VALUES (?, 'steamspy_seed')
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
