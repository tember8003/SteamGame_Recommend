package SteamGame.recommend.infra.api;

import SteamGame.recommend.domain.recommendation.dto.ParsedConstraints;
import SteamGame.recommend.domain.recommendation.dto.SessionLength;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiConstraintServiceTest {

    MockWebServer server;
    GeminiConstraintService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        String constraintUrl = server.url("/v1beta/models/gemini-2.0-flash:generateContent").toString();
        service = new GeminiConstraintService(new ObjectMapper(), WebClient.builder(), "test", constraintUrl);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void parsesJsonObjectFromGeminiText() throws Exception {
        String geminiBody = """
                {"candidates":[{"content":{"parts":[{"text":
                "{\\"players\\":3,\\"budgetUsd\\":20,\\"sessionLength\\":\\"SHORT\\",\\"needsCoop\\":true,\\"moodText\\":\\"왁자지껄 협동\\",\\"excludeTags\\":[\\"Horror\\"]}"
                }]}}]}""";
        server.enqueue(new MockResponse().setBody(geminiBody).addHeader("Content-Type", "application/json"));

        ParsedConstraints pc = service.parseConstraints("친구 3명이랑 20달러 이하 협동, 호러 빼줘");

        assertThat(pc.players()).isEqualTo(3);
        assertThat(pc.budgetUsd()).isEqualTo(20);
        assertThat(pc.sessionLength()).isEqualTo(SessionLength.SHORT);
        assertThat(pc.needsCoop()).isTrue();
        assertThat(pc.moodText()).isEqualTo("왁자지껄 협동");
        assertThat(pc.excludeTags()).containsExactly("Horror");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).contains(":generateContent");
        assertThat(req.getPath()).contains("key=test");
    }

    @Test
    void returnsNullOnMalformed() {
        server.enqueue(new MockResponse().setBody("not json").addHeader("Content-Type", "application/json"));
        assertThat(service.parseConstraints("아무거나")).isNull();
    }

    @Test
    void invalidSessionLengthStringIsAbsorbedToNull() {
        String geminiBody = """
                {"candidates":[{"content":{"parts":[{"text":
                "{\\"players\\":null,\\"budgetUsd\\":null,\\"sessionLength\\":\\"UNKNOWN\\",\\"needsCoop\\":null,\\"moodText\\":null,\\"excludeTags\\":[]}"
                }]}}]}""";
        server.enqueue(new MockResponse().setBody(geminiBody).addHeader("Content-Type", "application/json"));

        ParsedConstraints pc = service.parseConstraints("아무 텍스트");

        assertThat(pc.players()).isNull();
        assertThat(pc.sessionLength()).isNull();
        assertThat(pc.excludeTags()).isEmpty();
    }

    @Test
    void excludeTagsFiltersOutValuesNotInTagList() {
        String geminiBody = """
                {"candidates":[{"content":{"parts":[{"text":
                "{\\"players\\":null,\\"budgetUsd\\":null,\\"sessionLength\\":null,\\"needsCoop\\":null,\\"moodText\\":null,\\"excludeTags\\":[\\"Horror\\",\\"NotARealTag\\"]}"
                }]}}]}""";
        server.enqueue(new MockResponse().setBody(geminiBody).addHeader("Content-Type", "application/json"));

        ParsedConstraints pc = service.parseConstraints("호러 말고 이상한태그도 빼줘");

        assertThat(pc.excludeTags()).containsExactly("Horror");
    }

    @Test
    void wrongTypedFieldsAreAbsorbedToNullInsteadOfCoerced() {
        String geminiBody = """
                {"candidates":[{"content":{"parts":[{"text":
                "{\\"players\\":\\"three\\",\\"budgetUsd\\":\\"twenty\\",\\"sessionLength\\":null,\\"needsCoop\\":\\"yes\\",\\"moodText\\":null,\\"excludeTags\\":[]}"
                }]}}]}""";
        server.enqueue(new MockResponse().setBody(geminiBody).addHeader("Content-Type", "application/json"));

        ParsedConstraints pc = service.parseConstraints("타입이 이상한 응답");

        assertThat(pc.players()).isNull();
        assertThat(pc.budgetUsd()).isNull();
        assertThat(pc.needsCoop()).isNull();
    }

    @Test
    void blankInput_returnsNullWithoutCallingApi() {
        assertThat(service.parseConstraints("")).isNull();
        assertThat(service.parseConstraints(null)).isNull();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void fallback_returnsNull() {
        assertThat(service.fallbackParse("x", new RuntimeException("boom"))).isNull();
    }
}
