package SteamGame.recommend.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
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
}
