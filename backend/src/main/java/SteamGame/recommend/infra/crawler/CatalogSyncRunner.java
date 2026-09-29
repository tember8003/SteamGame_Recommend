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
@ConditionalOnProperty(name = "app.crawler.run", havingValue = "catalog")
public class CatalogSyncRunner implements ApplicationRunner {

    private final CatalogSyncService service;
    private final ConfigurableApplicationContext context;

    public CatalogSyncRunner(CatalogSyncService service, ConfigurableApplicationContext context) {
        this.service = service;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== steam catalog sync starting ===");
        CatalogSyncService.SyncResult r = service.runSync();
        log.info("=== steam catalog sync done: pages={}, saved={}, failed={}, stop={} ===",
                r.pages(), r.saved(), r.failed(), r.stopReason().label());
        int exitCode = SpringApplication.exit(context, () -> 0);
        System.exit(exitCode);
    }
}
