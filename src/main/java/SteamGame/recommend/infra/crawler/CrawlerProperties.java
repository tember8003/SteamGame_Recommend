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
}
