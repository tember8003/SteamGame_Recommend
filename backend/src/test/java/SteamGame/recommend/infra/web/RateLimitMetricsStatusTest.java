package SteamGame.recommend.infra.web;

import SteamGame.recommend.config.RateLimitProperties;
import SteamGame.recommend.config.SecurityConfig;
import SteamGame.recommend.controller.MetricsController;
import SteamGame.recommend.domain.metrics.dto.MetricsStatusDTO;
import SteamGame.recommend.domain.metrics.service.MetricsDashboardService;
import SteamGame.recommend.domain.metrics.service.MetricsStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /api/metrics/status 가 RateLimitFilter 등록에서 빠지면 제한 없이 뚫려 이 테스트가 잡는다
@WebMvcTest(MetricsController.class)
@Import({SecurityConfig.class, RateLimitFilter.class, RateLimitProperties.class, ClientIpResolver.class})
@TestPropertySource(properties = "app.metrics.token=secret")
class RateLimitMetricsStatusTest {

    @Autowired MockMvc mvc;
    @MockitoBean MetricsDashboardService dashboardService;
    @MockitoBean MetricsStatusService statusService;

    private MockHttpServletRequestBuilder statusFrom(String ip) {
        return get("/api/metrics/status")
                .header("X-Metrics-Token", "secret")
                .with(req -> { req.setRemoteAddr(ip); return req; });
    }

    @Test
    void status_31stRequest_429() throws Exception {
        when(statusService.status()).thenReturn(new MetricsStatusDTO(
                new MetricsStatusDTO.Server("abc1234", "2026-11-08T00:00:00Z", "10"), List.of(),
                new MetricsStatusDTO.Errors("2026-11-08T00:00:00Z", List.of(), List.of())));

        for (int i = 0; i < 30; i++) {
            mvc.perform(statusFrom("10.4.0.1")).andExpect(status().isOk());
        }
        mvc.perform(statusFrom("10.4.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
