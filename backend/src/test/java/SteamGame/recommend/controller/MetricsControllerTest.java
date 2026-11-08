package SteamGame.recommend.controller;

import SteamGame.recommend.domain.metrics.DashboardRange;
import SteamGame.recommend.domain.metrics.dto.MetricsDashboardDTO;
import SteamGame.recommend.domain.metrics.dto.MetricsStatusDTO;
import SteamGame.recommend.domain.metrics.service.MetricsDashboardService;
import SteamGame.recommend.domain.metrics.service.MetricsStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MetricsControllerTest {

    private final MetricsDashboardService dashboardService = mock(MetricsDashboardService.class);
    private final MetricsStatusService statusService = mock(MetricsStatusService.class);

    private MetricsController controller(String token) {
        return new MetricsController(dashboardService, statusService, token);
    }

    @Test
    void dashboard_withMatchingToken_usesParsedRange() {
        MetricsController controller = controller("secret");
        when(dashboardService.dashboard(DashboardRange.D30)).thenReturn(null);

        ResponseEntity<MetricsDashboardDTO> res = controller.dashboard("30d", "secret");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getHeaders().getCacheControl()).contains("no-store");
        verify(dashboardService).dashboard(DashboardRange.D30);
    }

    @Test
    void dashboard_withoutRange_defaultsTo7d() {
        MetricsController controller = controller("secret");
        controller.dashboard(null, "secret");
        verify(dashboardService).dashboard(DashboardRange.D7);
    }

    @Test
    void dashboard_badRange_is400() {
        MetricsController controller = controller("secret");
        assertThatThrownBy(() -> controller.dashboard("1y", "secret"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(dashboardService);
    }

    // 토큰을 기간보다 먼저 검사해 토큰 없이는 400 도 보이지 않는다
    @Test
    void dashboard_wrongOrMissingOrUnsetToken_is401BeforeRangeCheck() {
        assertThat(controller("secret").dashboard("1y", "nope").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller("secret").dashboard("7d", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller("").dashboard("7d", "").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(dashboardService);
    }

    private static MetricsStatusDTO dummyStatus() {
        return new MetricsStatusDTO(
                new MetricsStatusDTO.Server("abc1234", "2026-11-08T00:00:00Z", "10"),
                List.of(new MetricsStatusDTO.Event("2026-11-08T00:00:00Z", "LANDING_VISIT", null, null, null, null, null)),
                new MetricsStatusDTO.Errors("2026-11-08T00:00:00Z", List.of(), List.of()));
    }

    @Test
    void status_withMatchingToken_returns200AndNoStore() {
        MetricsController controller = controller("secret");
        when(statusService.status()).thenReturn(dummyStatus());

        ResponseEntity<MetricsStatusDTO> res = controller.status("secret");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getHeaders().getCacheControl()).contains("no-store");
        assertThat(res.getBody()).isEqualTo(dummyStatus());
        verify(statusService).status();
    }

    @Test
    void status_wrongOrMissingOrUnsetToken_is401() {
        assertThat(controller("secret").status("nope").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller("secret").status(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller("").status("").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(statusService);
    }

    // 응답 DTO의 이벤트 칸에는 client_id를 담을 자리가 없어야 한다
    @Test
    void status_responseEventHasNoClientIdComponent() {
        assertThat(MetricsStatusDTO.Event.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("clientId", "client_id");
    }
}
