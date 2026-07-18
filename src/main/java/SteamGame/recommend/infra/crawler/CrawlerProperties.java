package SteamGame.recommend.infra.crawler;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.crawler")
public class CrawlerProperties {
    private String steamspyBaseUrl = "https://steamspy.com/api.php";
    private long pageDelayMs = 60000;
    private int maxPages = 100;
    private int minReviews = 50;

    public String getSteamspyBaseUrl() { return steamspyBaseUrl; }
    public void setSteamspyBaseUrl(String steamspyBaseUrl) { this.steamspyBaseUrl = steamspyBaseUrl; }
    public long getPageDelayMs() { return pageDelayMs; }
    public void setPageDelayMs(long pageDelayMs) { this.pageDelayMs = pageDelayMs; }
    public int getMaxPages() { return maxPages; }
    public void setMaxPages(int maxPages) { this.maxPages = maxPages; }
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
}
