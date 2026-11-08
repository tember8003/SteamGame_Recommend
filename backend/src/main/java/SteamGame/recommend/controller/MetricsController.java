package SteamGame.recommend.controller;

import SteamGame.recommend.domain.metrics.DashboardRange;
import SteamGame.recommend.domain.metrics.dto.MetricsDashboardDTO;
import SteamGame.recommend.domain.metrics.dto.MetricsStatusDTO;
import SteamGame.recommend.domain.metrics.service.MetricsDashboardService;
import SteamGame.recommend.domain.metrics.service.MetricsStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Tag(name = "metrics", description = "Recommendation Metrics Read API")
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsDashboardService dashboardService;
    private final MetricsStatusService statusService;
    private final String metricsToken;

    public MetricsController(MetricsDashboardService dashboardService, MetricsStatusService statusService,
                            @Value("${app.metrics.token:}") String metricsToken) {
        this.dashboardService = dashboardService;
        this.statusService = statusService;
        this.metricsToken = metricsToken;
    }

    @Operation(summary = "Read admin dashboard metrics for a period")
    @GetMapping("/dashboard")
    public ResponseEntity<MetricsDashboardDTO> dashboard(
            @RequestParam(required = false) String range,
            @RequestHeader(value = "X-Metrics-Token", required = false) String token) {
        // 토큰을 먼저 본다, 토큰 없이 기간 오류 응답으로 엔드포인트를 더듬지 못하게 함
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        DashboardRange r = DashboardRange.parse(range)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_RANGE"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dashboardService.dashboard(r));
    }

    // 기간과 무관한 지금 상태라 대시보드 응답에 섞지 않고 따로 둔다, 토큰/요청 제한/no-store는 대시보드와 같다
    @Operation(summary = "Read current server status regardless of period")
    @GetMapping("/status")
    public ResponseEntity<MetricsStatusDTO> status(
            @RequestHeader(value = "X-Metrics-Token", required = false) String token) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(statusService.status());
    }

    // 설정 토큰이 비어 있으면 항상 거부한다, 비교는 상수 시간으로 한다
    private boolean authorized(String token) {
        if (metricsToken == null || metricsToken.isEmpty() || token == null) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                metricsToken.getBytes(StandardCharsets.UTF_8));
    }
}
