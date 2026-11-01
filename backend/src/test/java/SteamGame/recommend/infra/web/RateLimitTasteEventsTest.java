package SteamGame.recommend.infra.web;

import SteamGame.recommend.config.RateLimitProperties;
import SteamGame.recommend.config.SecurityConfig;
import SteamGame.recommend.controller.EventController;
import SteamGame.recommend.controller.TasteController;
import SteamGame.recommend.domain.event.service.FunnelEventService;
import SteamGame.recommend.domain.feedback.service.FeedbackSuppressionService;
import SteamGame.recommend.domain.impression.service.ImpressionService;
import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import SteamGame.recommend.domain.taste.service.TasteRecommendService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 취향 테스트와 이벤트 엔드포인트의 IP당 제한, 테스트마다 다른 IP를 써서 버킷이 섞이지 않게 한다
@WebMvcTest({TasteController.class, EventController.class})
@Import({SecurityConfig.class, RateLimitFilter.class, RateLimitProperties.class, ClientIpResolver.class,
        TasteContentLoader.class})
class RateLimitTasteEventsTest {

    @Autowired MockMvc mvc;
    @MockitoBean TasteRecommendService recommendService;
    @MockitoBean FeedbackSuppressionService suppressionService;
    @MockitoBean ImpressionService impressionService;
    @MockitoBean FunnelEventService eventService;

    private MockHttpServletRequestBuilder postFrom(String path, String ip, String body) {
        return post(path)
                .with(req -> { req.setRemoteAddr(ip); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void assertLimitedAfter(String path, String ip, String body, int capacity) throws Exception {
        for (int i = 0; i < capacity; i++) {
            int code = mvc.perform(postFrom(path, ip, body)).andReturn().getResponse().getStatus();
            assertThat(code).as("request %d", i + 1).isNotEqualTo(429);
        }
        mvc.perform(postFrom(path, ip, body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("rate_limited"));
        // 다른 IP는 별도 버킷이라 통과한다
        int other = mvc.perform(postFrom(path, ip + "9", body)).andReturn().getResponse().getStatus();
        assertThat(other).isNotEqualTo(429);
    }

    @Test
    void events_121stRequest_429() throws Exception {
        assertLimitedAfter("/api/events", "10.1.0.1", "{\"type\":\"LANDING_VISIT\"}", 120);
    }

    @Test
    void tasteResult_31stRequest_429() throws Exception {
        // 버전이 달라 409로 끝나도 토큰은 쓴다
        assertLimitedAfter("/api/taste/result", "10.1.0.2", "{\"version\":\"v0\",\"answers\":[]}", 30);
    }

    @Test
    void tasteRecommend_31stRequest_429() throws Exception {
        // 없는 유형이라 404로 끝나도 토큰은 쓴다
        assertLimitedAfter("/api/taste/recommend", "10.1.0.3", "{\"code\":\"XXXX\"}", 30);
    }

    @Test
    void tasteQuestions_isNotLimited() throws Exception {
        for (int i = 0; i < 40; i++) {
            mvc.perform(get("/api/taste/questions").with(req -> { req.setRemoteAddr("10.1.0.4"); return req; }))
                    .andExpect(status().isOk());
        }
    }
}
