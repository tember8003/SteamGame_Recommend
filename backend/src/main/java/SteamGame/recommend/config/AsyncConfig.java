package SteamGame.recommend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    // 노출 적재 전용 실행기, 요청 스레드를 막지 않고 부하 시 스레드가 무한히 늘지 않게 경계를 둔다
    @Bean("impressionExecutor")
    public ThreadPoolTaskExecutor impressionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("impression-");
        // 큐까지 차면 조용히 버린다, 노출은 유실돼도 되는 텔레메트리라 요청 스레드로 되돌리지 않는다
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        // 종료 시 진행 중 적재를 마무리한다
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        // initialize 는 Spring 이 afterPropertiesSet 에서 부르므로 여기서 부르지 않는다, 부르면 실행기가 두 번 생성됨
        return executor;
    }
}
