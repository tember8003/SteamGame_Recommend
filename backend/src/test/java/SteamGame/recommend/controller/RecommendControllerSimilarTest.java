package SteamGame.recommend.controller;

import SteamGame.recommend.config.RateLimitProperties;
import SteamGame.recommend.config.SecurityConfig;
import SteamGame.recommend.domain.feedback.service.FeedbackSuppressionService;
import SteamGame.recommend.domain.impression.service.ImpressionService;
import SteamGame.recommend.domain.recommendation.dto.RecommendationResultDTO;
import SteamGame.recommend.domain.recommendation.service.MatchService;
import SteamGame.recommend.domain.recommendation.service.RecommendService;
import SteamGame.recommend.infra.web.ClientIpResolver;
import SteamGame.recommend.infra.web.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 프론트가 실제로 보내는 요청 본문 그대로 /similar 를 부른다
// Jackson 3 기본값(FIX_FIELD_NAME_UPPER_CASE_PREFIX)으로는 DTO 필드 GameName 이 JSON 키 "GameName" 이 되어
// 프론트의 "gameName" 을 못 읽고 400 이 났다(Boot 4 전환 직후 운영 장애), 그 회귀를 막는다
@WebMvcTest(RecommendController.class)
@Import({SecurityConfig.class, RateLimitFilter.class, RateLimitProperties.class, ClientIpResolver.class})
class RecommendControllerSimilarTest {

    @Autowired MockMvc mvc;
    @MockitoBean MatchService matchService;
    @MockitoBean RecommendService recommendService;
    @MockitoBean FeedbackSuppressionService suppressionService;
    @MockitoBean ImpressionService impressionService;

    @Test
    void frontendRequestBodyIsReadWithCamelCaseGameName() throws Exception {
        when(suppressionService.resolveExcludes(any(), any())).thenReturn(Set.of());
        when(recommendService.recommendBySimilarGame(any(), anyInt(), any(), any(), anySet()))
                .thenReturn(mock(RecommendationResultDTO.class));

        mvc.perform(post("/api/recommend/similar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameName\":\"Stardew Valley\",\"review\":300,\"korean_check\":true,"
                                + "\"free_check\":false,\"excludeAppids\":[],\"clientId\":\"c1\"}"))
                .andExpect(status().isOk());

        verify(recommendService).recommendBySimilarGame(eq("Stardew Valley"), eq(300), eq(true), eq(false), anySet());
    }

    // Jackson 2 때처럼 int 필드에 null 이 오면 0 으로 읽는다(Jackson 3 기본값은 400)
    @Test
    void nullForPrimitiveReviewIsReadAsZero() throws Exception {
        when(suppressionService.resolveExcludes(any(), any())).thenReturn(Set.of());
        when(recommendService.recommendBySimilarGame(any(), anyInt(), any(), any(), anySet()))
                .thenReturn(mock(RecommendationResultDTO.class));

        mvc.perform(post("/api/recommend/similar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameName\":\"Stardew Valley\",\"review\":null}"))
                .andExpect(status().isOk());

        verify(recommendService).recommendBySimilarGame(eq("Stardew Valley"), eq(0), any(), any(), anySet());
    }
}
