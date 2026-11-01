package SteamGame.recommend.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class WarnAndDiscardPolicyTest {

    @Test
    void rejection_logsWarnAndDoesNotThrow() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(AsyncConfig.WarnAndDiscardPolicy.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setRejectedExecutionHandler(new AsyncConfig.WarnAndDiscardPolicy("test"));
        executor.initialize();

        CountDownLatch block = new CountDownLatch(1);
        AtomicInteger ran = new AtomicInteger();
        try {
            // 하나는 실행 중, 하나는 큐, 세 번째는 거절된다
            executor.execute(() -> { try { block.await(); } catch (InterruptedException ignored) { } ran.incrementAndGet(); });
            executor.execute(ran::incrementAndGet);
            assertThatCode(() -> executor.execute(ran::incrementAndGet)).doesNotThrowAnyException();

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
            assertThat(appender.list.get(0).getFormattedMessage()).contains("test");
        } finally {
            block.countDown();
            executor.shutdown();
            logger.detachAppender(appender);
        }
    }

    @Test
    void burstLogsOncePerMinuteWithDroppedCount() {
        Logger logger = (Logger) LoggerFactory.getLogger(AsyncConfig.WarnAndDiscardPolicy.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            long[] now = {1_000_000L};
            Clock clock = new Clock() {
                @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() { return Instant.ofEpochMilli(now[0]); }
            };
            AsyncConfig.WarnAndDiscardPolicy policy = new AsyncConfig.WarnAndDiscardPolicy("test", clock);
            ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

            for (int i = 0; i < 5; i++) {
                policy.rejectedExecution(() -> { }, pool);
            }
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage()).contains("1개");

            now[0] += 61_000L;
            policy.rejectedExecution(() -> { }, pool);

            assertThat(appender.list).hasSize(2);
            // 첫 로그 뒤 4개(같은 분 안 4번)와 이번 1번을 합친 5개
            assertThat(appender.list.get(1).getFormattedMessage()).contains("5개");
            pool.shutdown();
        } finally {
            logger.detachAppender(appender);
        }
    }
}
