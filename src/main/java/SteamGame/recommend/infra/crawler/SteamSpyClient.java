package SteamGame.recommend.infra.crawler;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class SteamSpyClient {

    private final WebClient webClient;
    private final CrawlerProperties props;

    public SteamSpyClient(WebClient.Builder builder, CrawlerProperties props) {
        this.webClient = builder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.props = props;
    }

    /** {baseUrl}?request=all&page={page} 의 원본 JSON 본문을 반환한다. */
    public String fetchAllPage(int page) {
        return webClient.get()
                .uri(props.getSteamspyBaseUrl() + "?request=all&page=" + page)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    /** {baseUrl}?request=appdetails&appid={appid} 의 원본 JSON 본문을 반환한다. */
    public String fetchAppDetails(long appid) {
        return webClient.get()
                .uri(props.getSteamspyBaseUrl() + "?request=appdetails&appid=" + appid)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }
}
