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
    private final TagCooccurrenceRefresher cooccurrenceRefresher;
    private final ConfigurableApplicationContext context;

    public CatalogSyncRunner(CatalogSyncService service, TagCooccurrenceRefresher cooccurrenceRefresher,
                             ConfigurableApplicationContext context) {
        this.service = service;
        this.cooccurrenceRefresher = cooccurrenceRefresher;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== steam catalog sync starting ===");
        CatalogSyncService.SyncResult r = service.runSync();
        log.info("=== steam catalog sync done: pages={}, saved={}, failed={}, stop={} ===",
                r.pages(), r.saved(), r.failed(), r.stopReason().label());
        // 태그가 매일 바뀌므로 동기화가 성공한 뒤에만 태그 쌍 통계를 다시 계산함
        int pairs = cooccurrenceRefresher.refresh();
        log.info("=== tag cooccurrence done: pairs={} ===", pairs);
        int exitCode = SpringApplication.exit(context, () -> 0);
        System.exit(exitCode);
    }
}
