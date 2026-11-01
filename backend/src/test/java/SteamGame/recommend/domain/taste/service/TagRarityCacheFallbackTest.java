package SteamGame.recommend.domain.taste.service;

import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 한 번도 DB에서 읽어 오지 못한 캐시의 재시도와 대체 동작
class TagRarityCacheFallbackTest {

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    MutableClock clock = new MutableClock();
    TasteContentLoader loader = new TasteContentLoader(new ObjectMapper());
    TagRarityCache cache;
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    Logger logger = (Logger) LoggerFactory.getLogger(TagRarityCache.class);

    @BeforeEach
    void setUp() {
        // 게임이 0개라 갱신은 성공해도 맵이 빈 상태로 남는다
        when(jdbc.queryForObject("SELECT count(*) FROM games", Long.class)).thenReturn(0L);
        cache = new TagRarityCache(jdbc, loader, clock);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void emptyCacheTriesRefreshThenFallsBackToOne() {
        double r = cache.rarity("Strategy");

        assertThat(r).isEqualTo(1.0);
        verify(jdbc, times(1)).queryForObject(eq("SELECT count(*) FROM games"), eq(Long.class));
    }

    @Test
    void retryIsLimitedToOncePerMinute() {
        cache.rarity("Strategy");
        cache.rarity("Strategy");
        cache.rarity("Management");
        verify(jdbc, times(1)).queryForObject(eq("SELECT count(*) FROM games"), eq(Long.class));

        clock.advance(Duration.ofSeconds(61));
        cache.rarity("Strategy");

        verify(jdbc, times(2)).queryForObject(eq("SELECT count(*) FROM games"), eq(Long.class));
    }

    @Test
    void errorLogIsLimitedToOncePerMinute() {
        cache.rarity("Strategy");
        cache.rarity("Strategy");
        cache.rarity("Management");

        assertThat(appender.list.stream().filter(e -> e.getLevel() == Level.ERROR)).hasSize(1);

        clock.advance(Duration.ofSeconds(61));
        cache.rarity("Strategy");

        assertThat(appender.list.stream().filter(e -> e.getLevel() == Level.ERROR)).hasSize(2);
    }

    // 태그 하나(Strategy, 게임 10개)를 돌려주도록 jdbc 목을 맞춘다
    private void stubTags(long total) throws Exception {
        doReturn(total).when(jdbc).queryForObject("SELECT count(*) FROM games", Long.class);
        doAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("cnt")).thenReturn(10L);
            when(rs.getString("name")).thenReturn("Strategy");
            ((RowCallbackHandler) inv.getArgument(1)).processRow(rs);
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class));
    }

    @Test
    void firstRefreshFailsThenRetryAfter61SecondsGivesRealRarity() throws Exception {
        doThrow(new IllegalStateException("db down")).when(jdbc).queryForObject("SELECT count(*) FROM games", Long.class);

        cache.onReady();
        assertThat(cache.rarity("Strategy")).isEqualTo(1.0);

        stubTags(100L);
        clock.advance(Duration.ofSeconds(61));

        assertThat(cache.rarity("Strategy")).isCloseTo(Math.log(1.0 + 100.0 / 10.0), within(1e-9));
        assertThat(cache.rarity("없는태그")).isEqualTo(0.0);
    }

    @Test
    void startupFailureLogsErrorWithStackTrace() {
        doThrow(new IllegalStateException("db down")).when(jdbc).queryForObject("SELECT count(*) FROM games", Long.class);

        cache.onReady();

        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.ERROR);
            assertThat(e.getFormattedMessage()).contains("refresh failed");
            assertThat(e.getThrowableProxy()).isNotNull();
            assertThat(e.getThrowableProxy().getMessage()).isEqualTo("db down");
        });
    }

    @Test
    void failureAfterGoodLoadIsWarnWithStackTraceAndKeepsValues() throws Exception {
        stubTags(100L);
        cache.refresh();
        double before = cache.rarity("Strategy");
        appender.list.clear();
        doThrow(new IllegalStateException("db down")).when(jdbc).queryForObject("SELECT count(*) FROM games", Long.class);

        cache.refresh();

        assertThat(cache.rarity("Strategy")).isEqualTo(before);
        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getThrowableProxy()).isNotNull();
        });
        assertThat(appender.list.stream().filter(e -> e.getLevel() == Level.ERROR)).isEmpty();
    }

    @Test
    void emptyRefreshAfterGoodLoadKeepsPreviousValuesAndLogsError() throws Exception {
        stubTags(100L);
        cache.refresh();
        double before = cache.rarity("Strategy");
        assertThat(before).isGreaterThan(0.0);
        appender.list.clear();
        doReturn(0L).when(jdbc).queryForObject("SELECT count(*) FROM games", Long.class);

        cache.refresh();

        assertThat(cache.rarity("Strategy")).isEqualTo(before);
        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.ERROR);
            assertThat(e.getFormattedMessage()).contains("keeping previous values");
        });
    }
}
