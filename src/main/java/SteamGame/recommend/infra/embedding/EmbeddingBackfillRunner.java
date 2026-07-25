package SteamGame.recommend.infra.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 임베딩 백필 원샷 러너. `--app.crawler.run=embed` 로 기동 시에만 활성화되고, 배치 후 JVM 을 종료한다.
 * 크롤러 러너(seed/detail)와 같은 게이트 패턴.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.crawler.run", havingValue = "embed")
public class EmbeddingBackfillRunner implements ApplicationRunner {

    private final EmbeddingBackfillService service;
    private final ConfigurableApplicationContext context;

    public EmbeddingBackfillRunner(EmbeddingBackfillService service, ConfigurableApplicationContext context) {
        this.service = service;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== embedding backfill starting ===");
        EmbeddingBackfillService.EmbedResult result = service.runBackfill();
        log.info("=== embedding backfill done: processed={}, failed={} ===", result.processed(), result.failed());
        int exitCode = SpringApplication.exit(context, () -> 0);
        System.exit(exitCode);
    }
}
