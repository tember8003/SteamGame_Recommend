package SteamGame.recommend.controller;

import SteamGame.recommend.config.RateLimitProperties;
import SteamGame.recommend.config.SecurityConfig;
import SteamGame.recommend.domain.recommendation.dto.MatchExplanation;
import SteamGame.recommend.domain.recommendation.dto.MatchResultDTO;
import SteamGame.recommend.domain.recommendation.dto.RecommendationCriteria;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.feedback.service.FeedbackSuppressionService;
import SteamGame.recommend.domain.impression.service.ImpressionService;
import SteamGame.recommend.domain.recommendation.service.MatchService;
import SteamGame.recommend.domain.recommendation.service.RecommendService;
import SteamGame.recommend.infra.web.ClientIpResolver;
import SteamGame.recommend.infra.web.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 컨트롤러 생성자가 RecommendService, MatchService 둘 다 요구해 둘 다 목으로 채움
// RateLimitFilter 는 SecurityConfig 생성자 의존이라 슬라이스에 함께 들여옴, 필터 자체 동작은 이 슬라이스 밖 수기 검증 항목임
@WebMvcTest(RecommendController.class)
@Import({SecurityConfig.class, RateLimitFilter.class, RateLimitProperties.class, ClientIpResolver.class})
class RecommendControllerMatchTest {

    @Autowired MockMvc mvc;
    @MockitoBean MatchService matchService;
    @MockitoBean RecommendService recommendService;
    @MockitoBean FeedbackSuppressionService suppressionService;
    @MockitoBean ImpressionService impressionService;

    @Test
    void validRequestReturns200WithShape() throws Exception {
        when(matchService.recommend(any())).thenReturn(sampleResult());

        mvc.perform(post("/api/recommend/match").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"players\":4,\"budgetUsd\":20,\"moodTags\":[\"코옵\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteria").exists())
                .andExpect(jsonPath("$.games").isArray())
                .andExpect(jsonPath("$.explanations").isArray());
    }

    @Test
    void negativePlayersReturns400() throws Exception {
        mvc.perform(post("/api/recommend/match").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"players\":0}"))
                .andExpect(status().isBadRequest()); // 기존 handleValidation 이 @Valid 위반을 잡음
    }

    @Test
    void badSessionEnumReturns400() throws Exception {
        mvc.perform(post("/api/recommend/match").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionLength\":\"HUGE\"}"))
                .andExpect(status().isBadRequest()); // 열거형 역직렬화 실패가 HttpMessageNotReadableException 으로 400 이 됨
    }

    @Test
    void emptyBodyReturns200WithPopularResult() throws Exception {
        when(matchService.recommend(any())).thenReturn(sampleResult());

        mvc.perform(post("/api/recommend/match").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games").isArray());
    }

    // 슬라이스 응답 형태만 검증하는 최소 결과, 값 자체의 의미는 MatchServiceTest 가 다룸
    private MatchResultDTO sampleResult() {
        RecommendationCriteria criteria = RecommendationCriteria.builder().review(0).build();
        SteamAppDTO game = new SteamAppDTO();
        game.setAppid(730L);
        game.setName("Counter-Strike 2");
        MatchExplanation explanation = new MatchExplanation(List.of(), List.of());
        return new MatchResultDTO(criteria, List.of(game), List.of(), List.of(explanation), false);
    }
}
