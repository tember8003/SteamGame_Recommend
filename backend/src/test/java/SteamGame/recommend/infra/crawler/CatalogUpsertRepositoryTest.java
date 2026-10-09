package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.AppTag;
import SteamGame.recommend.infra.crawler.dto.StoreItem;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CatalogUpsertRepository.class)
public class CatalogUpsertRepositoryTest extends PostgresTestBase {

    @Autowired CatalogUpsertRepository repo;
    @Autowired JdbcTemplate jdbc;

    static StoreItem item(String description, List<AppTag> tags, int reviews) {
        return new StoreItem(570L, "Dota 2", description, "https://img/570", true, true, "Strategy",
                null, LocalDate.of(2013, 7, 9), "Valve", "Valve",
                reviews, new BigDecimal("82.00"), "Very Positive", tags);
    }

    Integer votes(String tag) {
        return jdbc.queryForObject("""
                SELECT gt.votes FROM game_tags gt JOIN tags t ON t.id = gt.tag_id
                JOIN games g ON g.id = gt.game_id WHERE g.appid = 570 AND t.name = ?""", Integer.class, tag);
    }

    @Test
    void upsertItem_insertsGameReviewSummaryAndTags() {
        repo.upsertItem(item("desc", List.of(new AppTag("MOBA", 900), new AppTag("Strategy", 700)), 1450000));

        assertThat(jdbc.queryForObject("SELECT name FROM games WHERE appid=570", String.class)).isEqualTo("Dota 2");
        assertThat(jdbc.queryForObject("SELECT review_count FROM games WHERE appid=570", Integer.class)).isEqualTo(1450000);
        assertThat(jdbc.queryForObject("SELECT korean_support FROM games WHERE appid=570", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT release_date FROM games WHERE appid=570", LocalDate.class))
                .isEqualTo(LocalDate.of(2013, 7, 9));
        assertThat(jdbc.queryForObject("SELECT total_reviews FROM reviews WHERE appid=570", Integer.class)).isEqualTo(1450000);
        assertThat(jdbc.queryForObject("SELECT review_score FROM reviews WHERE appid=570", String.class)).isEqualTo("Very Positive");
        assertThat(votes("MOBA")).isEqualTo(900);
        assertThat(votes("Strategy")).isEqualTo(700);
    }

    @Test
    void upsertItem_rerun_replacesTagsAndUpdatesCounts_withoutDuplicates() {
        repo.upsertItem(item("desc", List.of(new AppTag("MOBA", 900), new AppTag("Strategy", 700)), 100));
        repo.upsertItem(item("desc", List.of(new AppTag("MOBA", 950), new AppTag("Free to Play", 10)), 200));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM games WHERE appid=570", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE appid=570", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT review_count FROM games WHERE appid=570", Integer.class)).isEqualTo(200);
        assertThat(votes("MOBA")).isEqualTo(950);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM game_tags gt JOIN games g ON g.id = gt.game_id WHERE g.appid = 570""",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void upsertItem_duplicateTagNames_keepFirstWithoutError() {
        repo.upsertItem(item("desc", List.of(new AppTag("MOBA", 900), new AppTag("MOBA", 5), new AppTag("Strategy", 700)), 100));

        assertThat(votes("MOBA")).isEqualTo(900);
        assertThat(votes("Strategy")).isEqualTo(700);
    }

    @Test
    void upsertItem_emptyTags_keepsExistingTags() {
        repo.upsertItem(item("desc", List.of(new AppTag("MOBA", 900)), 100));
        repo.upsertItem(item("desc", List.of(), 100));

        assertThat(votes("MOBA")).isEqualTo(900);
    }

    @Test
    void upsertItem_preservesReviewTextAndDescription() {
        jdbc.update("INSERT INTO games (appid, name, description) VALUES (570, 'Old', 'keep me')");
        jdbc.update("INSERT INTO reviews (appid, total_reviews, positive_review_text, negative_review_text) "
                + "VALUES (570, 1, 'great', 'bad')");

        repo.upsertItem(item(null, List.of(new AppTag("MOBA", 1)), 300));

        assertThat(jdbc.queryForObject("SELECT description FROM games WHERE appid=570", String.class)).isEqualTo("keep me");
        assertThat(jdbc.queryForObject("SELECT name FROM games WHERE appid=570", String.class)).isEqualTo("Dota 2");
        assertThat(jdbc.queryForObject("SELECT positive_review_text FROM reviews WHERE appid=570", String.class)).isEqualTo("great");
        assertThat(jdbc.queryForObject("SELECT negative_review_text FROM reviews WHERE appid=570", String.class)).isEqualTo("bad");
        assertThat(jdbc.queryForObject("SELECT total_reviews FROM reviews WHERE appid=570", Integer.class)).isEqualTo(300);
    }

    static StoreItem sparse(Integer price, boolean isFree, Boolean korean) {
        return new StoreItem(570L, "Dota 2", null, null, isFree, korean, null, price,
                null, null, null, 100, null, null, List.of());
    }

    @Test
    void upsertItem_preservesNullableColumns() {
        jdbc.update("INSERT INTO games (appid, name, image_url, genres, developer, publisher) "
                + "VALUES (570, 'Old', 'https://old/img', 'RPG', 'OldDev', 'OldPub')");

        repo.upsertItem(sparse(null, false, null));

        assertThat(jdbc.queryForObject("SELECT image_url FROM games WHERE appid=570", String.class)).isEqualTo("https://old/img");
        assertThat(jdbc.queryForObject("SELECT genres FROM games WHERE appid=570", String.class)).isEqualTo("RPG");
        assertThat(jdbc.queryForObject("SELECT developer FROM games WHERE appid=570", String.class)).isEqualTo("OldDev");
        assertThat(jdbc.queryForObject("SELECT publisher FROM games WHERE appid=570", String.class)).isEqualTo("OldPub");
    }

    @Test
    void upsertItem_freeGame_clearsPrice_andKoreanFlagOverwritten() {
        jdbc.update("INSERT INTO games (appid, name, price, is_free, korean_support) VALUES (570, 'Old', 1999, false, true)");

        repo.upsertItem(sparse(null, true, false));

        assertThat(jdbc.queryForObject("SELECT price FROM games WHERE appid=570", Integer.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT is_free FROM games WHERE appid=570", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT korean_support FROM games WHERE appid=570", Boolean.class)).isFalse();
    }

    @Test
    void upsertItem_missingLanguagesAndPrice_keepOldValues() {
        jdbc.update("INSERT INTO games (appid, name, price, is_free, korean_support) VALUES (570, 'Old', 1999, false, true)");

        repo.upsertItem(sparse(null, false, null));

        assertThat(jdbc.queryForObject("SELECT price FROM games WHERE appid=570", Integer.class)).isEqualTo(1999);
        assertThat(jdbc.queryForObject("SELECT korean_support FROM games WHERE appid=570", Boolean.class)).isTrue();
    }

    @Test
    void upsertItem_newGameWithNullKorean_storesFalse() {
        repo.upsertItem(sparse(null, false, null));

        assertThat(jdbc.queryForObject("SELECT korean_support FROM games WHERE appid=570", Boolean.class)).isFalse();
    }
}
