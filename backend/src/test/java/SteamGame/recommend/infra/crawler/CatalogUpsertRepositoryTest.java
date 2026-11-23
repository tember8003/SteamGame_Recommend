package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.AppTag;
import SteamGame.recommend.infra.crawler.dto.StoreItem;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
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

    java.sql.Timestamp contentUpdatedAt() {
        return jdbc.queryForObject("SELECT content_updated_at FROM games WHERE appid=570", java.sql.Timestamp.class);
    }

    static StoreItem item(String name, String description, String genres, List<AppTag> tags, int reviews) {
        return new StoreItem(570L, name, description, "https://img/570", true, true, genres,
                null, LocalDate.of(2013, 7, 9), "Valve", "Valve",
                reviews, new BigDecimal("82.00"), "Very Positive", tags);
    }

    // 임베딩 재계산의 기준이라, 매일 같은 값이 다시 들어오는 것만으로는 바뀌지 않아야 한다
    // 태그, 리뷰 수, 이미지처럼 임베딩 글의 뜻을 바꾸지 않는 칸도 기준에서 뺀다
    @Test
    void upsertItem_sameContentOrNonContentChange_keepsContentUpdatedAt() {
        repo.upsertItem(item("Dota 2", "desc", "Strategy", List.of(new AppTag("MOBA", 900)), 100));
        assertThat(contentUpdatedAt()).isNull();

        repo.upsertItem(item("Dota 2", "desc", "Strategy", List.of(new AppTag("MOBA", 950), new AppTag("Free to Play", 10)), 200));
        assertThat(contentUpdatedAt()).isNull();

        // 응답에서 설명이나 장르가 빠지면 기존 값을 지키므로 내용 변경이 아니다
        repo.upsertItem(item("Dota 2", null, null, List.of(new AppTag("MOBA", 950)), 200));
        assertThat(contentUpdatedAt()).isNull();
    }

    @Test
    void upsertItem_nameOrDescriptionChange_stampsContentUpdatedAt() {
        repo.upsertItem(item("Dota 2", "desc", "Strategy", List.of(), 100));

        repo.upsertItem(item("Dota 2", "new desc", "Strategy", List.of(), 100));
        assertThat(contentUpdatedAt()).isNotNull();

        jdbc.update("UPDATE games SET content_updated_at = NULL WHERE appid = 570");
        repo.upsertItem(item("Dota 2: Reborn", "new desc", "Strategy", List.of(), 100));
        assertThat(contentUpdatedAt()).isNotNull();
    }

    // 장르는 상위 20개 태그에서 가중치 순으로 뽑은 값이라 순서와 구성이 자주 흔들림, 태그와 같은 이유로 기준에서 뺀다
    @Test
    void upsertItem_genresOnlyChange_doesNotStamp() {
        repo.upsertItem(item("Dota 2", "desc", "Action, Strategy", List.of(), 100));

        repo.upsertItem(item("Dota 2", "desc", "Strategy, Action", List.of(), 100));
        repo.upsertItem(item("Dota 2", "desc", "Strategy, Action, Free To Play", List.of(), 100));

        assertThat(contentUpdatedAt()).isNull();
    }

    // 이미 찍힌 시각은 다음 실행에서 같은 내용이 다시 들어와도 지켜야 한다, 지우면 아직 다시 임베딩하지 않은 변경이 사라진다
    @Test
    void upsertItem_keepsExistingContentUpdatedAt_whenContentUnchanged() {
        repo.upsertItem(item("Dota 2", "desc", "Strategy", List.of(), 100));
        repo.upsertItem(item("Dota 2", "new desc", "Strategy", List.of(), 100));
        java.sql.Timestamp stamped = contentUpdatedAt();
        assertThat(stamped).isNotNull();

        repo.upsertItem(item("Dota 2", "new desc", "Action", List.of(new AppTag("MOBA", 1)), 999));

        assertThat(contentUpdatedAt()).isEqualTo(stamped);
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
