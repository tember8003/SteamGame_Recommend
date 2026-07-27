package SteamGame.recommend.infra.crawler;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class SteamStoreClient {

    private final WebClient webClient;
    private final CrawlerProperties props;

    public SteamStoreClient(WebClient.Builder builder, CrawlerProperties props) {
        this.webClient = builder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.props = props;
    }

    // {storeBaseUrl}/api/appdetails?appids={id}&cc=us&l=english 원본 JSON 반환
    public String fetchAppDetails(long appid) {
        return webClient.get()
                .uri(props.getStoreBaseUrl() + "/api/appdetails?appids=" + appid + "&cc=us&l=english")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // {storeBaseUrl}/appreviews/{id}?json=1&num_per_page=20&language=english&purchase_type=all&filter=all 원본 JSON 반환
    public String fetchAppReviews(long appid) {
        return webClient.get()
                .uri(props.getStoreBaseUrl() + "/appreviews/" + appid
                        + "?json=1&num_per_page=20&language=english&purchase_type=all&filter=all")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }
}
