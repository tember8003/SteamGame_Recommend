package SteamGame.recommend.infra.crawler;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
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

    // 같은 요청에 늘 같은 순서를 주고 전체를 한 번씩만 돌려주는 정렬을 씀
    // 리뷰 순(30)은 같은 요청에도 순서가 달라 페이지를 넘기면 게임이 빠지고 겹침
    static final int SORT_STABLE = 2;

    // IUserReviewsService 열거값, 도움순 0, 리뷰 전체 0, 구매 경로 전체 1(Steam 구매만은 0)
    static final int REVIEWS_FILTER_HELPFUL = 0;
    static final int REVIEWS_TYPE_ALL = 0;
    static final int REVIEWS_PURCHASE_ALL = 1;
    static final int REVIEWS_PER_PAGE = 20;

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
        query.put("sort", SORT_STABLE);
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

    // 상점의 appreviews 주소는 2026-10 하순에 폐기돼 상점 첫 화면으로 넘어가므로 Web API 리뷰 서비스를 부름
    // 예전 요청(filter=all, purchase_type=all, language=english)과 같은 기준이 되게 도움순, 구매 경로 전체로 맞춤
    // 도움순은 최근 30일 안에서 고르고 없으면 기간을 넓힘(day_range_used), languages 를 비우면 영어만 옴(2026-11-23 실제 응답으로 확인)
    // 성공 여부는 본문의 success 가 아니라 HTTP 상태로 판단함
    public String fetchAppReviews(long appid) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("appid", appid);
        input.put("filter", REVIEWS_FILTER_HELPFUL);
        input.put("review_type", REVIEWS_TYPE_ALL);
        input.put("purchase_type", REVIEWS_PURCHASE_ALL);
        input.put("num_per_page", REVIEWS_PER_PAGE);

        URI uri = UriComponentsBuilder.fromUriString(props.getSteamApiBaseUrl() + "/IUserReviewsService/GetAppReviews/v1/")
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
