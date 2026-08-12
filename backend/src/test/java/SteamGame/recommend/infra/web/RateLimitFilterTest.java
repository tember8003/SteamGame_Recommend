package SteamGame.recommend.infra.web;

import SteamGame.recommend.config.RateLimitProperties;
import SteamGame.recommend.config.SecurityConfig;
import SteamGame.recommend.controller.RecommendController;
import SteamGame.recommend.domain.feedback.service.FeedbackSuppressionService;
import SteamGame.recommend.domain.recommendation.dto.RecommendationResultDTO;
import SteamGame.recommend.domain.recommendation.service.MatchService;
import SteamGame.recommend.domain.recommendation.service.RecommendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecommendController.class)
@Import({SecurityConfig.class, RateLimitFilter.class, RateLimitProperties.class, ClientIpResolver.class})
class RateLimitFilterTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean RecommendService recommendService;
    // RecommendController 가 MatchService 를 주입받으므로 슬라이스 컨텍스트에도 목이 필요하다
    @MockitoBean MatchService matchService;
    @MockitoBean FeedbackSuppressionService suppressionService;

    @BeforeEach
    void stubSuppression() {
        // 요청에 client_id 가 없어 억제는 no-op 이지만, 컨트롤러가 유효 제외 집합을 계산하므로 non-null 을 돌려줌
        given(suppressionService.resolveExcludes(any(), any())).willReturn(Set.of());
    }

    private RecommendationResultDTO dummy() {
        return new RecommendationResultDTO(List.of(), List.of(), List.of());
    }

    private MockHttpServletRequestBuilder inputFrom(String ip) {
        return post("/api/recommend/input")
                .with(req -> { req.setRemoteAddr(ip); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"emotional story games\",\"review\":0}");
    }

    @Test
    void input_over10PerMinute_returns429WithRetryAfter() throws Exception {
        given(recommendService.selectInfo(any(), anyInt(), any(), any(), any())).willReturn(dummy());

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(inputFrom("10.0.0.1")).andExpect(status().isOk());
        }
        mockMvc.perform(inputFrom("10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("rate_limited"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    @Test
    void input_bucketsAreIndependentPerIp() throws Exception {
        given(recommendService.selectInfo(any(), anyInt(), any(), any(), any())).willReturn(dummy());

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(inputFrom("10.0.0.2")).andExpect(status().isOk());
        }
        // 다른 IP 는 별도 버킷이라 여전히 통과함
        mockMvc.perform(inputFrom("10.0.0.3")).andExpect(status().isOk());
    }

    @Test
    void randomEndpoint_isNotRateLimited() throws Exception {
        given(recommendService.findGame(any(), anyInt(), any(), any(), any(), any(), any())).willReturn(dummy());

        // /random 은 대상 3개에 없어 11번째도 통과함
        for (int i = 0; i < 11; i++) {
            mockMvc.perform(post("/api/recommend/random")
                            .with(req -> { req.setRemoteAddr("10.0.0.4"); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tags\":[\"RPG\"],\"review\":0}"))
                    .andExpect(status().isOk());
        }
    }
}
