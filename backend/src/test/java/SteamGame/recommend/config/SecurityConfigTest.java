package SteamGame.recommend.config;

import SteamGame.recommend.controller.RecommendController;
import SteamGame.recommend.domain.feedback.service.FeedbackSuppressionService;
import SteamGame.recommend.domain.impression.service.ImpressionService;
import SteamGame.recommend.domain.recommendation.dto.RecommendationResultDTO;
import SteamGame.recommend.domain.recommendation.service.MatchService;
import SteamGame.recommend.domain.recommendation.service.RecommendService;
import SteamGame.recommend.infra.web.ClientIpResolver;
import SteamGame.recommend.infra.web.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecommendController.class)
@Import({SecurityConfig.class, RateLimitFilter.class, RateLimitProperties.class, ClientIpResolver.class})
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean RecommendService recommendService;
    // RecommendController 가 MatchService 를 주입받으므로 슬라이스 컨텍스트에도 목이 필요하다
    @MockitoBean MatchService matchService;
    @MockitoBean FeedbackSuppressionService suppressionService;
    @MockitoBean ImpressionService impressionService;

    @BeforeEach
    void stubSuppression() {
        // 요청에 client_id 가 없어 억제는 no-op 이지만, 컨트롤러가 유효 제외 집합을 계산하므로 non-null 을 돌려줌
        given(suppressionService.resolveExcludes(any(), any())).willReturn(Set.of());
    }

    private RecommendationResultDTO dummy() {
        return new RecommendationResultDTO(List.of(), List.of(), List.of());
    }

    @Test
    void permitAll_allowsRequestWithoutAuth_andSetsSecurityHeaders() throws Exception {
        given(recommendService.selectInfo(any(), anyInt(), any(), any(), any()))
                .willReturn(dummy());

        mockMvc.perform(post("/api/recommend/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"emotional story games\",\"review\":0}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().exists("Referrer-Policy"));
    }

    @Test
    void cors_allowsConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/recommend/input")
                        .header("Origin", "http://localhost:8888")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8888"));
    }

    @Test
    void cors_rejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/recommend/input")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    // 코드리뷰 지적사항: CORS 허용 메서드 외의 프리플라이트는 거부되어야 함
    @Test
    void cors_rejectsDisallowedMethod() throws Exception {
        mockMvc.perform(options("/api/recommend/input")
                        .header("Origin", "http://localhost:8888")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
    }

    // 코드리뷰 지적사항: HSTS 헤더는 보안 요청(HTTPS)에서만 부여되므로 secure 요청으로 검증함
    @Test
    void secureRequest_includesStrictTransportSecurityHeader() throws Exception {
        given(recommendService.selectInfo(any(), anyInt(), any(), any(), any()))
                .willReturn(dummy());

        mockMvc.perform(post("/api/recommend/input")
                        .secure(true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"emotional story games\",\"review\":0}"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Strict-Transport-Security"));
    }

    // 코드리뷰 지적사항: allowed-origins 가 비어있으면 모든 오리진을 거부해야 함(오픈 실패 방지)
    @Test
    void corsConfigurationSource_withBlankOrigins_allowsNoOrigin() {
        SecurityConfig blankOriginConfig = new SecurityConfig("", mock(RateLimitFilter.class));

        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) blankOriginConfig.corsConfigurationSource();
        CorsConfiguration registered = source.getCorsConfigurations().get("/**");

        assertThat(registered.getAllowedOriginPatterns()).isNullOrEmpty();
    }

    // Vercel 프리뷰 오리진은 배포마다 바뀌므로 패턴 매칭으로 허용되는지 확인함
    @Test
    void corsConfigurationSource_withVercelPattern_matchesPreviewOrigin() {
        SecurityConfig patternConfig = new SecurityConfig(
                "https://*.vercel.app,https://steam-recommend.duckdns.org", mock(RateLimitFilter.class));

        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) patternConfig.corsConfigurationSource();
        CorsConfiguration registered = source.getCorsConfigurations().get("/**");

        assertThat(registered.checkOrigin("https://sgr-frontend-git-feature.vercel.app")).isNotNull();
        assertThat(registered.checkOrigin("https://evil.example")).isNull();
    }
}
