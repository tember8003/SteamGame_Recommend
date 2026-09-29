package SteamGame.recommend.infra.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class SteamWebApiClient {

    // 리뷰 수 많은 순, 실측으로 확인한 IStoreQueryService 정렬 값
    static final int SORT_BY_REVIEWS = 30;

    private final WebClient webClient;
    private final CrawlerProperties props;
    private final ObjectMapper objectMapper;

    public SteamWebApiClient(WebClient.Builder builder, CrawlerProperties props, ObjectMapper objectMapper) {
        // Query 한 페이지가 약 3.5MB 라 버퍼를 넉넉히 잡음
        this.webClient = builder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public String fetchTagList() {
        URI uri = UriComponentsBuilder.fromUriString(props.getSteamApiBaseUrl() + "/IStoreService/GetTagList/v1/")
                .queryParam("language", "english")
                .build().toUri();
        return getWithRetry(uri);
    }

    public String fetchQueryPage(int start, int count) {
        ObjectNode input = objectMapper.createObjectNode();
        ObjectNode query = input.putObject("query");
        query.put("start", start);
        query.put("count", count);
        query.put("sort", SORT_BY_REVIEWS);
        ObjectNode filters = query.putObject("filters");
        filters.put("released_only", true);
        filters.putObject("type_filters").put("include_games", true);
        input.putObject("context").put("language", "english").put("country_code", "US");
        input.putObject("data_request")
                .put("include_tag_count", 20)
                .put("include_reviews", true)
                .put("include_release", true)
                .put("include_basic_info", true)
                .put("include_assets", true)
                .put("include_supported_languages", true);

        // JSON 의 중괄호가 URI 템플릿 변수로 읽히지 않게 값으로 넘겨 인코딩함
        URI uri = UriComponentsBuilder.fromUriString(props.getSteamApiBaseUrl() + "/IStoreQueryService/Query/v1/")
                .queryParam("input_json", "{input}")
                .encode()
                .buildAndExpand(input.toString())
                .toUri();
        return getWithRetry(uri);
    }

    String getWithRetry(URI uri) {
        List<Long> backoff = props.getCatalogRetryBackoffMs();
        for (int attempt = 0; ; attempt++) {
            try {
                return webClient.get().uri(uri)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(60))
                        .block();
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean retryable = status == 429 || e.getStatusCode().is5xxServerError();
                if (!retryable || attempt >= backoff.size()) throw e;
                log.warn("steam web api {} returned {} — retry {} after {}ms",
                        uri.getPath(), status, attempt + 1, backoff.get(attempt));
                sleep(backoff.get(attempt));
            }
        }
    }

    private void sleep(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
