package SteamGame.recommend.infra.crawler;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.crawler")
public class CrawlerProperties {
    private int minReviews = 50;

    public int getMinReviews() { return minReviews; }
    public void setMinReviews(int minReviews) { this.minReviews = minReviews; }

    private String storeBaseUrl = "https://store.steampowered.com";
    private long detailDelayMs = 1500;
    private int dailyDetailBatchSize = 200;
    private int reviewSampleMaxChars = 2000;

    public String getStoreBaseUrl() { return storeBaseUrl; }
    public void setStoreBaseUrl(String storeBaseUrl) { this.storeBaseUrl = storeBaseUrl; }
    public long getDetailDelayMs() { return detailDelayMs; }
    public void setDetailDelayMs(long detailDelayMs) { this.detailDelayMs = detailDelayMs; }
    public int getDailyDetailBatchSize() { return dailyDetailBatchSize; }
    public void setDailyDetailBatchSize(int dailyDetailBatchSize) { this.dailyDetailBatchSize = dailyDetailBatchSize; }
    public int getReviewSampleMaxChars() { return reviewSampleMaxChars; }
    public void setReviewSampleMaxChars(int reviewSampleMaxChars) { this.reviewSampleMaxChars = reviewSampleMaxChars; }

    private String steamApiBaseUrl = "https://api.steampowered.com";
    private int catalogPageSize = 1000;
    private int catalogMaxPages = 200;
    private long catalogCallDelayMs = 1000;
    private List<Long> catalogRetryBackoffMs = List.of(30000L, 60000L, 120000L);

    public String getSteamApiBaseUrl() { return steamApiBaseUrl; }
    public void setSteamApiBaseUrl(String steamApiBaseUrl) { this.steamApiBaseUrl = steamApiBaseUrl; }
    public int getCatalogPageSize() { return catalogPageSize; }
    public void setCatalogPageSize(int catalogPageSize) { this.catalogPageSize = catalogPageSize; }
    public int getCatalogMaxPages() { return catalogMaxPages; }
    public void setCatalogMaxPages(int catalogMaxPages) { this.catalogMaxPages = catalogMaxPages; }
    public long getCatalogCallDelayMs() { return catalogCallDelayMs; }
    public void setCatalogCallDelayMs(long catalogCallDelayMs) { this.catalogCallDelayMs = catalogCallDelayMs; }
    public List<Long> getCatalogRetryBackoffMs() { return catalogRetryBackoffMs; }
    public void setCatalogRetryBackoffMs(List<Long> catalogRetryBackoffMs) { this.catalogRetryBackoffMs = catalogRetryBackoffMs; }
}
