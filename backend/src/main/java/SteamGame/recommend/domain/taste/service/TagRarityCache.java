package SteamGame.recommend.domain.taste.service;

import SteamGame.recommend.domain.taste.content.TagWeight;
import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import SteamGame.recommend.domain.taste.content.TasteType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

// 태그별 희소도 ln(1 + 전체 게임 수 / 태그가 붙은 게임 수)를 메모리에 둔다
// 기동 직후와 매일 04:00(서버 시간대)에 다시 계산한다
@Slf4j
@Component
public class TagRarityCache {

    static final long RETRY_INTERVAL_MILLIS = 60_000L;
    // 비어 있을 때 쓰는 대체 희소도, 원래 가중치만으로 순위를 매긴다
    static final double FALLBACK_RARITY = 1.0;

    private final JdbcTemplate jdbc;
    private final TasteContentLoader contentLoader;
    private final Clock clock;
    private volatile Map<String, Double> rarities = Map.of();
    // 마지막 갱신 시도와 마지막 오류 로그 시각, 처음에는 한 번 허용되도록 멀리 과거로 둔다
    private final AtomicLong lastAttempt = new AtomicLong(Long.MIN_VALUE / 2);
    private final AtomicLong lastErrorLog = new AtomicLong(Long.MIN_VALUE / 2);

    @Autowired
    public TagRarityCache(JdbcTemplate jdbc, TasteContentLoader contentLoader) {
        this(jdbc, contentLoader, Clock.systemUTC());
    }

    TagRarityCache(JdbcTemplate jdbc, TasteContentLoader contentLoader, Clock clock) {
        this.jdbc = jdbc;
        this.contentLoader = contentLoader;
        this.clock = clock;
    }

    // 모르는 태그는 0.0
    // 한 번도 DB에서 읽어 오지 못했으면 분당 한 번만 다시 시도하고, 그래도 비면 모든 태그를 1.0으로 본다
    public double rarity(String tag) {
        Map<String, Double> current = rarities;
        if (current.isEmpty()) {
            retryIfDue();
            current = rarities;
            if (current.isEmpty()) {
                logUnavailable();
                return FALLBACK_RARITY;
            }
        }
        return current.getOrDefault(tag, 0.0);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        refresh();
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void refresh() {
        lastAttempt.set(clock.millis());
        Map<String, Double> next;
        Long total;
        try {
            total = jdbc.queryForObject("SELECT count(*) FROM games", Long.class);
            next = new HashMap<>();
            if (total != null && total > 0) {
                long gameCount = total;
                jdbc.query("""
                        SELECT t.name, count(*) AS cnt
                        FROM game_tags gt JOIN tags t ON t.id = gt.tag_id
                        GROUP BY t.name
                        """, rs -> {
                    long cnt = rs.getLong("cnt");
                    if (cnt > 0) {
                        next.put(rs.getString("name"), Math.log(1.0 + (double) gameCount / cnt));
                    }
                });
            }
        } catch (RuntimeException e) {
            // 실패해도 이전 값을 그대로 쓴다, 읽어 온 값이 아직 없으면(기동 직후나 재시도 중) 대체 값으로 순위를 매기게 되므로 ERROR로 올린다
            if (rarities.isEmpty()) {
                log.error("tag rarity refresh failed, no previous values", e);
            } else {
                log.warn("tag rarity refresh failed, keeping previous values", e);
            }
            return;
        }
        if (next.isEmpty()) {
            // 빈 결과로 덮어쓰면 모든 희소도가 0이 되어 순위가 무너진다
            if (rarities.isEmpty()) {
                // 이어서 rarity()가 사용 불가 ERROR를 남기므로 여기서는 WARN으로 둔다
                log.warn("tag rarity refresh yielded no data: games={}", total);
            } else {
                log.error("tag rarity refresh yielded no data, keeping previous values: games={}", total);
            }
            return;
        }
        rarities = Map.copyOf(next);
        log.info("tag rarity refreshed: games={}, tags={}", total, next.size());
        try {
            warnMissingContentTags(next);
        } catch (RuntimeException e) {
            log.warn("checking content tags against DB failed", e);
        }
    }

    // 여러 스레드가 동시에 와도 시각을 먼저 차지한 하나만 다시 시도한다
    private void retryIfDue() {
        long now = clock.millis();
        long prev = lastAttempt.get();
        if (now - prev >= RETRY_INTERVAL_MILLIS && lastAttempt.compareAndSet(prev, now)) {
            refresh();
        }
    }

    private void logUnavailable() {
        long now = clock.millis();
        long prev = lastErrorLog.get();
        if (now - prev >= RETRY_INTERVAL_MILLIS && lastErrorLog.compareAndSet(prev, now)) {
            log.error("tag rarity unavailable, ranking with raw weights (rarity=1.0)");
        }
    }

    // 문구 데이터의 태그가 DB에 하나도 없으면 오타일 수 있어 알린다
    private void warnMissingContentTags(Map<String, Double> loaded) {
        Set<String> missing = new TreeSet<>();
        for (TasteType type : contentLoader.content().types()) {
            for (TagWeight tw : type.tagWeights()) {
                if (!loaded.containsKey(tw.tag())) {
                    missing.add(tw.tag());
                }
            }
        }
        if (!missing.isEmpty()) {
            log.warn("taste content tags missing in DB: {}", missing);
        }
    }
}
