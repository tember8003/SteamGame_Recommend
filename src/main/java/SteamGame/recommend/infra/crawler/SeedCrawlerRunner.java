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
@ConditionalOnProperty(name = "app.crawler.run", havingValue = "seed")
public class SeedCrawlerRunner implements ApplicationRunner {

    private final SeedCrawlerService service;
    private final ConfigurableApplicationContext context;

    public SeedCrawlerRunner(SeedCrawlerService service, ConfigurableApplicationContext context) {
        this.service = service;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== SteamSpy seed crawler starting ===");
        SeedCrawlerService.SeedResult result = service.runSeed();
        log.info("=== SteamSpy seed done: pages={}, gamesUpserted={}, capped={} ===",
                result.pagesFetched(), result.gamesUpserted(), result.cappedByMaxPages());
        int exitCode = SpringApplication.exit(context, () -> 0);
        System.exit(exitCode);
    }
}
