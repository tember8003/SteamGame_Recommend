package SteamGame.recommend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    // 추천 노출 기록을 DB에 저장하는 일만 맡는 비동기 작업자 묶음, 요청 스레드를 막지 않고 부하가 몰려도 스레드가 끝없이 늘지 않게 상한을 둔다
    @Bean("impressionExecutor")
    public ThreadPoolTaskExecutor impressionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("impression-");
        // 큐까지 차면 조용히 버린다, 노출은 유실돼도 되는 텔레메트리라 요청 스레드로 되돌리지 않는다
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        // 서버가 꺼질 때 저장 중인 작업을 마저 끝낸다
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        // initialize 는 Spring 이 afterPropertiesSet 에서 부르므로 여기서 부르지 않는다, 부르면 실행기가 두 번 생성됨
        return executor;
    }

    // 사용자 행동 이벤트를 DB에 저장하는 일만 맡는 비동기 작업자 묶음, 대기열이 차면 작업을 버리되 버렸다는 사실을 WARN 로그로 남긴다
    @Bean("funnelEventExecutor")
    public ThreadPoolTaskExecutor funnelEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("funnel-event-");
        executor.setRejectedExecutionHandler(new WarnAndDiscardPolicy("funnel-event"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }

    // 대기열이 가득 차서 받지 못한 작업은 버리고 WARN 로그 한 줄을 남긴다, 버린 작업의 내용(이벤트 종류, clientId)은 여기서 알 수 없어 싣지 않는다
    public static class WarnAndDiscardPolicy implements RejectedExecutionHandler {

        private static final Logger log = LoggerFactory.getLogger(WarnAndDiscardPolicy.class);

        private final String name;

        public WarnAndDiscardPolicy(String name) {
            this.name = name;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("{} 대기열이 가득 차서 저장 작업 하나를 버림, 대기 중 {}개, 처리 중 {}개", name, executor.getQueue().size(), executor.getActiveCount());
        }
    }
}
