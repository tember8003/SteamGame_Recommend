package SteamGame.recommend.domain.event.service;

import SteamGame.recommend.domain.event.entity.FunnelEventType;
import SteamGame.recommend.domain.event.entity.ShareChannel;
import SteamGame.recommend.domain.taste.TasteDbTestConfig;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 @Async("funnelEventExecutor")가 전용 실행기를 찾아 그 위에서 돌고 행이 저장되는지 본다
// 비동기 스레드의 저장은 자기 트랜잭션으로 커밋되므로 테스트 트랜잭션은 끈다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TasteDbTestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FunnelEventAsyncTest extends PostgresTestBase {

    private static final String CLIENT = "async-it";

    @Autowired FunnelEventService service;
    @Autowired @Qualifier("funnelEventExecutor") ThreadPoolTaskExecutor executor;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM funnel_event WHERE client_id = ?", CLIENT);
    }

    private static boolean await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(25);
        }
        return condition.getAsBoolean();
    }

    @Test
    void recordRunsOnDedicatedExecutorAndSavesRow() throws Exception {
        long completedBefore = executor.getThreadPoolExecutor().getCompletedTaskCount();

        service.record(CLIENT, FunnelEventType.SHARE_CLICK, "gmhs", ShareChannel.LINK);

        boolean saved = await(() -> jdbc.queryForObject(
                "SELECT count(*) FROM funnel_event WHERE client_id = ?", Integer.class, CLIENT) == 1);
        assertThat(saved).as("row saved within 10s").isTrue();
        assertThat(jdbc.queryForObject("SELECT ref FROM funnel_event WHERE client_id = ?", String.class, CLIENT))
                .isEqualTo("GMHS");

        // 전용 실행기가 이 작업을 끝낸 것으로 센다, 호출 스레드에서 돌았다면 늘지 않는다
        boolean counted = await(() -> executor.getThreadPoolExecutor().getCompletedTaskCount() > completedBefore);
        assertThat(counted).as("funnelEventExecutor completed the task").isTrue();
        assertThat(executor.getThreadNamePrefix()).isEqualTo("funnel-event-");
    }
}
