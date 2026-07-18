package SteamGame.recommend.infra.crawler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.crawler.run", havingValue = "detail")
public class DetailCrawlerRunner implements ApplicationRunner {

    private final DetailCrawlerService service;
    private final ConfigurableApplicationContext context;

    public DetailCrawlerRunner(DetailCrawlerService service, ConfigurableApplicationContext context) {
        this.service = service;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== detail rolling crawler starting ===");
        DetailCrawlerService.DetailResult result = service.runDetailBatch();
        log.info("=== detail rolling done: processed={}, failed={} ===", result.processed(), result.failed());
        int exitCode = SpringApplication.exit(context, () -> 0);
        System.exit(exitCode);
    }
}
