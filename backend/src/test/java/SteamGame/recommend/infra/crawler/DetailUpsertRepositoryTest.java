package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.ReviewDetail;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
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
    void upsertReviewDetail_writesOnlyTextColumns_andKeepsSummary() {
        jdbc.update("INSERT INTO reviews (appid, total_reviews, positive_percentage, review_score) "
                + "VALUES (?, 555, 91.00, 'Very Positive')", appid);

        repo.upsertReviewDetail(new ReviewDetail(appid, "Mixed", 200, new BigDecimal("55.00"), "great", "bad"));

        assertThat(jdbc.queryForObject("SELECT positive_review_text FROM reviews WHERE appid=?", String.class, appid)).isEqualTo("great");
        assertThat(jdbc.queryForObject("SELECT negative_review_text FROM reviews WHERE appid=?", String.class, appid)).isEqualTo("bad");
        assertThat(jdbc.queryForObject("SELECT total_reviews FROM reviews WHERE appid=?", Integer.class, appid)).isEqualTo(555);
        assertThat(jdbc.queryForObject("SELECT review_score FROM reviews WHERE appid=?", String.class, appid)).isEqualTo("Very Positive");

        repo.upsertReviewDetail(new ReviewDetail(appid, "Mixed", 200, new BigDecimal("55.00"), "ok", "meh"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE appid=?", Integer.class, appid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT positive_review_text FROM reviews WHERE appid=?", String.class, appid)).isEqualTo("ok");
    }

    // 대시보드가 작업 보고와 별개로 실제 저장 여부를 세는 근거라, 본문을 저장할 때만 시각이 찍혀야 한다
    @Test
    void upsertReviewDetail_stampsTextUpdatedAt_onlyWhenTextIsWritten() {
        jdbc.update("INSERT INTO reviews (appid, total_reviews) VALUES (?, 1)", appid);
        assertThat(jdbc.queryForObject("SELECT text_updated_at FROM reviews WHERE appid=?", java.sql.Timestamp.class, appid)).isNull();

        repo.upsertReviewDetail(new ReviewDetail(appid, "Mixed", 200, new BigDecimal("55.00"), "great", null));
        java.sql.Timestamp first = jdbc.queryForObject("SELECT text_updated_at FROM reviews WHERE appid=?", java.sql.Timestamp.class, appid);
        assertThat(first).isNotNull();

        repo.upsertReviewDetail(new ReviewDetail(appid, "Mixed", 200, new BigDecimal("55.00"), "again", null));
        assertThat(jdbc.queryForObject("SELECT text_updated_at FROM reviews WHERE appid=?", java.sql.Timestamp.class, appid))
                .isAfterOrEqualTo(first);
    }

    // 도움순 20개가 전부 긍정인 게임이 흔하다(Hollow Knight 실제 응답 20개 모두 긍정), 빈 쪽은 기존 본문을 지우지 않는다
    @Test
    void upsertReviewDetail_keepsExistingSide_whenNewResponseHasOnlyOneSide() {
        repo.upsertReviewDetail(new ReviewDetail(appid, "Mixed", 200, new BigDecimal("55.00"), "old good", "old bad"));

        repo.upsertReviewDetail(new ReviewDetail(appid, "Mixed", 200, new BigDecimal("55.00"), "new good", null));

        assertThat(jdbc.queryForObject("SELECT positive_review_text FROM reviews WHERE appid=?", String.class, appid)).isEqualTo("new good");
        assertThat(jdbc.queryForObject("SELECT negative_review_text FROM reviews WHERE appid=?", String.class, appid)).isEqualTo("old bad");
    }

    @Test
    void upsertReviewDetail_insertsRowWhenMissing() {
        repo.upsertReviewDetail(new ReviewDetail(appid, "Mixed", 200, new BigDecimal("55.00"), "great", null));
        assertThat(jdbc.queryForObject("SELECT positive_review_text FROM reviews WHERE appid=?", String.class, appid)).isEqualTo("great");
        assertThat(jdbc.queryForObject("SELECT total_reviews FROM reviews WHERE appid=?", Integer.class, appid)).isEqualTo(0);
    }

    @Test
    void selectNextToCrawl_prefersNeverCrawled_thenOldest() {
        // shared seedGame() fixture also has last_crawled_at=null; stamp it so it doesn't
        // compete in the "never crawled" bucket and skew the LIMIT-2 result below.
        jdbc.update("UPDATE games SET last_crawled_at = now() WHERE appid = ?", appid);
        long a = 700001L, b = 700002L, c = 700003L;
        jdbc.update("INSERT INTO games (appid, name, last_crawled_at, review_count) VALUES (?,?,?,?)", a, "A", null, 10L);
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
    void selectNextToCrawl_amongNeverCrawled_prefersMoreReviews() {
        repo.markCrawled(appid, OffsetDateTime.now());
        // 리뷰가 적은 쪽을 먼저 넣고 appid 도 작게 줌, 삽입 순서나 appid 순서로는 통과하지 못하게 함
        long lo = 700010L, hi = 700011L;
        jdbc.update("INSERT INTO games (appid, name, review_count) VALUES (?,?,?)", lo, "Lo", 60);
        jdbc.update("INSERT INTO games (appid, name, review_count) VALUES (?,?,?)", hi, "Hi", 90000);
        try {
            List<Long> next = repo.selectNextToCrawl(2);
            assertThat(next).containsExactly(hi, lo);   // 리뷰 수로 줄 세움
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
