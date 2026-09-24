package SteamGame.recommend.controller;

import SteamGame.recommend.domain.metrics.dto.MetricsOverviewDTO;
import SteamGame.recommend.domain.metrics.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Tag(name = "metrics", description = "Recommendation Metrics Read API")
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private static final int TOP_N = 20;

    private final MetricsService metricsService;
    private final String metricsToken;

    public MetricsController(MetricsService metricsService,
                            @Value("${app.metrics.token:}") String metricsToken) {
        this.metricsService = metricsService;
        this.metricsToken = metricsToken;
    }

    @Operation(summary = "Read recommendation metrics overview")
    @GetMapping("/overview")
    public ResponseEntity<MetricsOverviewDTO> overview(
            @RequestHeader(value = "X-Metrics-Token", required = false) String token) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(metricsService.overview(TOP_N));
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
