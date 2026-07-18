package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.AppTag;
import SteamGame.recommend.infra.crawler.dto.ReviewDetail;
import SteamGame.recommend.infra.crawler.dto.StorefrontDetail;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"steam.api.key=test", "spring.ai.google.api-key=test"})
class DetailUpsertRepositoryTest extends PostgresTestBase {

    @Autowired DetailUpsertRepository repo;
    @Autowired JdbcTemplate jdbc;

    long appid = 424242L;

    @BeforeEach
    void seedGame() {
        jdbc.update("INSERT INTO games (appid, name) VALUES (?, ?) ON CONFLICT (appid) DO NOTHING", appid, "Seed Game");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM game_tags WHERE game_id IN (SELECT id FROM games WHERE appid=?)", appid);
        jdbc.update("DELETE FROM reviews WHERE appid=?", appid);
        jdbc.update("DELETE FROM games WHERE appid=?", appid);
        jdbc.update("DELETE FROM tags WHERE name IN ('MOBA','Strategy','Indie')");
    }

    @Test
    void upsertTags_insertsWeightedTags_andIsIdempotentReplace() {
        repo.upsertTags(appid, List.of(new AppTag("MOBA", 500), new AppTag("Strategy", 300)));

        Long gameId = repo.findGameId(appid);
        assertThat(jdbc.queryForObject(
                "SELECT votes FROM game_tags gt JOIN tags t ON t.id=gt.tag_id WHERE gt.game_id=? AND t.name='MOBA'",
                Integer.class, gameId)).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_tags WHERE game_id=?", Integer.class, gameId)).isEqualTo(2);

        // re-crawl with a different tag set + updated votes → full replace, no dupes
        repo.upsertTags(appid, List.of(new AppTag("MOBA", 999), new AppTag("Indie", 10)));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_tags WHERE game_id=?", Integer.class, gameId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT votes FROM game_tags gt JOIN tags t ON t.id=gt.tag_id WHERE gt.game_id=? AND t.name='MOBA'",
                Integer.class, gameId)).isEqualTo(999);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_tags gt JOIN tags t ON t.id=gt.tag_id WHERE gt.game_id=? AND t.name='Strategy'",
                Integer.class, gameId)).isEqualTo(0);
    }

    @Test
    void upsertTags_forUnseededAppid_isNoOp() {
        repo.upsertTags(999000111L, List.of(new AppTag("MOBA", 1)));  // no such game
        assertThat(repo.findGameId(999000111L)).isNull();
    }

    @Test
    void upsertStorefront_updatesConfirmedColumns() {
        repo.upsertStorefront(new StorefrontDetail(appid, "Real Name", "desc", "http://img", true, true,
                "Action, Strategy", 1999, LocalDate.of(2013, 7, 9), "Valve", "Valve"));

        assertThat(jdbc.queryForObject("SELECT name FROM games WHERE appid=?", String.class, appid)).isEqualTo("Real Name");
        assertThat(jdbc.queryForObject("SELECT is_free FROM games WHERE appid=?", Boolean.class, appid)).isTrue();
        assertThat(jdbc.queryForObject("SELECT korean_support FROM games WHERE appid=?", Boolean.class, appid)).isTrue();
        assertThat(jdbc.queryForObject("SELECT genres FROM games WHERE appid=?", String.class, appid)).isEqualTo("Action, Strategy");
        assertThat(jdbc.queryForObject("SELECT price FROM games WHERE appid=?", Integer.class, appid)).isEqualTo(1999);
        assertThat(jdbc.queryForObject("SELECT release_date FROM games WHERE appid=?", LocalDate.class, appid)).isEqualTo(LocalDate.of(2013, 7, 9));
    }

    @Test
    void upsertReviewDetail_insertsThenUpdates_idempotent() {
        repo.upsertReviewDetail(new ReviewDetail(appid, "Very Positive", 100, new BigDecimal("80.00"), "great", "bad"));
        assertThat(jdbc.queryForObject("SELECT review_score FROM reviews WHERE appid=?", String.class, appid)).isEqualTo("Very Positive");
        assertThat(jdbc.queryForObject("SELECT positive_review_text FROM reviews WHERE appid=?", String.class, appid)).isEqualTo("great");

        repo.upsertReviewDetail(new ReviewDetail(appid, "Mixed", 200, new BigDecimal("55.00"), "ok", "meh"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE appid=?", Integer.class, appid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT total_reviews FROM reviews WHERE appid=?", Integer.class, appid)).isEqualTo(200);
    }

    @Test
    void selectNextToCrawl_prefersNeverCrawled_thenOldest() {
        // shared seedGame() fixture also has last_crawled_at=null; stamp it so it doesn't
        // compete in the "never crawled" bucket and skew the LIMIT-2 result below.
        jdbc.update("UPDATE games SET last_crawled_at = now() WHERE appid = ?", appid);
        long a = 700001L, b = 700002L, c = 700003L;
        jdbc.update("INSERT INTO games (appid, name, last_crawled_at, owners_estimate) VALUES (?,?,?,?)", a, "A", null, 10L);
        jdbc.update("INSERT INTO games (appid, name, last_crawled_at) VALUES (?,?, now() - interval '10 days')", b, "B");
        jdbc.update("INSERT INTO games (appid, name, last_crawled_at) VALUES (?,?, now() - interval '1 day')", c, "C");
        try {
            List<Long> next = repo.selectNextToCrawl(2);
            assertThat(next).containsExactly(a, b);   // null first, then oldest
        } finally {
            jdbc.update("DELETE FROM games WHERE appid IN (?,?,?)", a, b, c);
        }
    }

    @Test
    void selectNextToCrawl_amongNeverCrawled_prefersHigherOwners() {
        // move the shared 424242 fixture out of the never-crawled bucket so it can't interfere
        repo.markCrawled(appid, OffsetDateTime.now());
        long hi = 700010L, lo = 700011L;
        jdbc.update("INSERT INTO games (appid, name, owners_estimate) VALUES (?,?,?)", hi, "Hi", 900000L);
        jdbc.update("INSERT INTO games (appid, name, owners_estimate) VALUES (?,?,?)", lo, "Lo", 100L);
        try {
            List<Long> next = repo.selectNextToCrawl(2);
            assertThat(next).containsExactly(hi, lo);   // higher owners first among never-crawled
        } finally {
            jdbc.update("DELETE FROM games WHERE appid IN (?,?)", hi, lo);
        }
    }

    @Test
    void markCrawled_setsTimestamp() {
        repo.markCrawled(appid, OffsetDateTime.now());
        assertThat(jdbc.queryForObject("SELECT last_crawled_at FROM games WHERE appid=?", java.sql.Timestamp.class, appid)).isNotNull();
    }
}
