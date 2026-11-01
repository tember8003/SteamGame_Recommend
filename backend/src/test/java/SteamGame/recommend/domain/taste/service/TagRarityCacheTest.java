package SteamGame.recommend.domain.taste.service;

import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import SteamGame.recommend.support.PostgresTestBase;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import SteamGame.recommend.domain.taste.TasteDbTestConfig;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TasteDbTestConfig.class)
class TagRarityCacheTest extends PostgresTestBase {

    private static final long BASE = 9_200_000L;

    @Autowired TagRarityCache cache;
    @Autowired TasteContentLoader loader;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("INSERT INTO tags (name) VALUES ('TR_A'), ('TR_B')");
        for (int i = 0; i < 10; i++) {
            jdbc.update("INSERT INTO games (appid, name) VALUES (?, ?)", BASE + i, "g" + i);
            jdbc.update("INSERT INTO game_tags (game_id, tag_id, votes) VALUES ((SELECT id FROM games WHERE appid=?), (SELECT id FROM tags WHERE name='TR_A'), 1)", BASE + i);
        }
        jdbc.update("INSERT INTO game_tags (game_id, tag_id, votes) VALUES ((SELECT id FROM games WHERE appid=?), (SELECT id FROM tags WHERE name='TR_B'), 1)", BASE);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM game_tags WHERE game_id IN (SELECT id FROM games WHERE appid BETWEEN ? AND ?)", BASE, BASE + 99);
        jdbc.update("DELETE FROM games WHERE appid BETWEEN ? AND ?", BASE, BASE + 99);
        jdbc.update("DELETE FROM tags WHERE name LIKE 'TR_%'");
    }

    @Test
    void rarityIsLogOfInverseFrequency() {
        cache.refresh();

        // 다른 테스트 데이터가 있어도 되도록 실제 전체 게임 수로 기대값을 구한다
        long total = jdbc.queryForObject("SELECT count(*) FROM games", Long.class);
        assertThat(cache.rarity("TR_A")).isCloseTo(Math.log(1.0 + total / 10.0), within(1e-9));
        assertThat(cache.rarity("TR_B")).isCloseTo(Math.log(1.0 + total / 1.0), within(1e-9));
        assertThat(cache.rarity("없는태그")).isEqualTo(0.0);
    }

    @Test
    void warnsAboutContentTagsMissingInDb() {
        Logger logger = (Logger) LoggerFactory.getLogger(TagRarityCache.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            cache.refresh();
        } finally {
            logger.detachAppender(appender);
        }

        String contentTag = loader.content().types().get(0).tagWeights().get(0).tag();
        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains("missing in DB").contains(contentTag);
        });
    }

    @Test
    void refreshFailureKeepsPreviousValues() {
        JdbcTemplate spy = spy(jdbc);
        TagRarityCache target = new TagRarityCache(spy, loader);
        target.refresh();
        double before = target.rarity("TR_A");
        assertThat(before).isGreaterThan(0.0);
        doThrow(new IllegalStateException("db down")).when(spy).queryForObject(anyString(), eq(Long.class));

        target.refresh();

        assertThat(target.rarity("TR_A")).isEqualTo(before);
    }
}
