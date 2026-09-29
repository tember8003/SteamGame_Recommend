package SteamGame.recommend.infra.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SteamWebApiClientTest {

    MockWebServer server;
    SteamWebApiClient client;
    final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        CrawlerProperties props = new CrawlerProperties();
        props.setSteamApiBaseUrl(server.url("").toString().replaceAll("/$", ""));
        props.setCatalogRetryBackoffMs(List.of(0L, 0L, 0L));
        client = new SteamWebApiClient(WebClient.builder(), props, om);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    MockResponse ok(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    void fetchTagList_callsGetTagListInEnglish() throws Exception {
        server.enqueue(ok("{\"response\":{\"tags\":[]}}"));

        assertThat(client.fetchTagList()).contains("\"tags\"");
        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo("/IStoreService/GetTagList/v1/");
        assertThat(req.getRequestUrl().queryParameter("language")).isEqualTo("english");
    }

    @Test
    void fetchQueryPage_sendsSortedPagedQuery() throws Exception {
        server.enqueue(ok("{\"response\":{}}"));

        client.fetchQueryPage(2000, 1000);

        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo("/IStoreQueryService/Query/v1/");
        JsonNode input = om.readTree(req.getRequestUrl().queryParameter("input_json"));
        assertThat(input.at("/query/start").asInt()).isEqualTo(2000);
        assertThat(input.at("/query/count").asInt()).isEqualTo(1000);
        assertThat(input.at("/query/sort").asInt()).isEqualTo(30);
        assertThat(input.at("/query/filters/released_only").asBoolean()).isTrue();
        assertThat(input.at("/query/filters/type_filters/include_games").asBoolean()).isTrue();
        assertThat(input.at("/data_request/include_tag_count").asInt()).isEqualTo(20);
        assertThat(input.at("/data_request/include_reviews").asBoolean()).isTrue();
        assertThat(input.at("/data_request/include_supported_languages").asBoolean()).isTrue();
        assertThat(input.at("/context/country_code").asText()).isEqualTo("US");
    }

    @Test
    void retriesOn429_thenReturnsBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(ok("{\"ok\":1}"));

        assertThat(client.fetchTagList()).contains("\"ok\"");
        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void givesUpAfterThreeRetries() {
        for (int i = 0; i < 4; i++) server.enqueue(new MockResponse().setResponseCode(429));

        assertThatThrownBy(() -> client.fetchTagList()).isInstanceOf(WebClientResponseException.class);
        assertThat(server.getRequestCount()).isEqualTo(4);
    }

    @Test
    void doesNotRetryOn400() {
        server.enqueue(new MockResponse().setResponseCode(400));

        assertThatThrownBy(() -> client.fetchQueryPage(0, 1000)).isInstanceOf(WebClientResponseException.class);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
}
