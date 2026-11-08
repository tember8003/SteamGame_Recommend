package SteamGame.recommend.controller;

import SteamGame.recommend.domain.metrics.DashboardRange;
import SteamGame.recommend.domain.metrics.dto.MetricsDashboardDTO;
import SteamGame.recommend.domain.metrics.service.MetricsDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MetricsControllerTest {

    private final MetricsDashboardService dashboardService = mock(MetricsDashboardService.class);

    @Test
    void dashboard_withMatchingToken_usesParsedRange() {
        MetricsController controller = new MetricsController(dashboardService, "secret");
        when(dashboardService.dashboard(DashboardRange.D30)).thenReturn(null);

        ResponseEntity<MetricsDashboardDTO> res = controller.dashboard("30d", "secret");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getHeaders().getCacheControl()).contains("no-store");
        verify(dashboardService).dashboard(DashboardRange.D30);
    }

    @Test
    void dashboard_withoutRange_defaultsTo7d() {
        MetricsController controller = new MetricsController(dashboardService, "secret");
        controller.dashboard(null, "secret");
        verify(dashboardService).dashboard(DashboardRange.D7);
    }

    @Test
    void dashboard_badRange_is400() {
        MetricsController controller = new MetricsController(dashboardService, "secret");
        assertThatThrownBy(() -> controller.dashboard("1y", "secret"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(dashboardService);
    }

    // 토큰을 기간보다 먼저 검사해 토큰 없이는 400 도 보이지 않는다
    @Test
    void dashboard_wrongOrMissingOrUnsetToken_is401BeforeRangeCheck() {
        assertThat(new MetricsController(dashboardService, "secret").dashboard("1y", "nope").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(new MetricsController(dashboardService, "secret").dashboard("7d", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(new MetricsController(dashboardService, "").dashboard("7d", "").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(dashboardService);
    }
}
