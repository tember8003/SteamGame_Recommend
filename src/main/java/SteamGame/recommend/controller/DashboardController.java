package SteamGame.recommend.controller;

import SteamGame.recommend.domain.dashboard.dto.DashboardDTO;
import SteamGame.recommend.domain.dashboard.dto.GameLibraryStatsDTO;
import SteamGame.recommend.domain.dashboard.dto.RecommendationInsightDTO;
import SteamGame.recommend.domain.dashboard.dto.UserProfileSummary;
import SteamGame.recommend.domain.dashboard.service.DashboardService;
import SteamGame.recommend.exception.DashboardException;
import SteamGame.recommend.exception.SteamApiException;

import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Retrieve user dashboard
     */
    @GetMapping("/{steamId}")
    public ResponseEntity<DashboardDTO> getDashboard(
            @PathVariable
            @Pattern(regexp = "\\d{17}", message = "Invalid Steam ID format")
            String steamId) {

        try {
            log.info("Dashboard retrieval request: steamId={}", steamId);

            // Additional Steam ID validation
            validateSteamIdFormat(steamId);

            DashboardDTO dashboard = dashboardService.generateDashboard(steamId);

            log.info("Dashboard retrieval successful: steamId={}", steamId);
            return ResponseEntity.ok(dashboard);

        } catch (SteamApiException e) {
            log.warn("Dashboard retrieval failed due to Steam API error: steamId={}, message={}",
                    steamId, e.getMessage());

            // Return basic dashboard even on Steam API errors
            DashboardDTO errorDashboard = createErrorDashboardForSteamApi(steamId, e);
            return ResponseEntity.status(HttpStatus.OK).body(errorDashboard);

        } catch (DashboardException e) {
            log.error("Dashboard generation error: steamId={}, type={}, message={}",
                    steamId, e.getErrorType(), e.getMessage());

            // Return basic dashboard for dashboard generation errors too
            DashboardDTO errorDashboard = createErrorDashboardForDashboard(steamId, e);
            return ResponseEntity.status(HttpStatus.OK).body(errorDashboard);

        } catch (Exception e) {
            log.error("Unexpected dashboard retrieval error: steamId={}", steamId, e);

            // Return basic dashboard for unexpected errors too
            DashboardDTO errorDashboard = createGenericErrorDashboard(steamId);
            return ResponseEntity.status(HttpStatus.OK).body(errorDashboard);
        }
    }

    /**
     * Force refresh dashboard
     */
    @PostMapping("/{steamId}/refresh")
    public ResponseEntity<DashboardDTO> refreshDashboard(
            @PathVariable
            @Pattern(regexp = "\\d{17}", message = "Invalid Steam ID format")
            String steamId) {

        try {
            log.info("Dashboard force refresh request: steamId={}", steamId);

            validateSteamIdFormat(steamId);

            DashboardDTO dashboard = dashboardService.refreshDashboard(steamId);

            log.info("Dashboard force refresh successful: steamId={}", steamId);
            return ResponseEntity.ok(dashboard);

        } catch (Exception e) {
            log.error("Dashboard force refresh error: steamId={}", steamId, e);

            // Try to return existing cached data if refresh fails
            try {
                DashboardDTO fallbackDashboard = dashboardService.generateDashboard(steamId);
                return ResponseEntity.ok(fallbackDashboard);
            } catch (Exception fallbackException) {
                DashboardDTO errorDashboard = createGenericErrorDashboard(steamId);
                return ResponseEntity.status(HttpStatus.OK).body(errorDashboard);
            }
        }
    }

    /**
     * Invalidate dashboard cache
     */
    @DeleteMapping("/{steamId}/cache")
    public ResponseEntity<Void> invalidateCache(
            @PathVariable
            @Pattern(regexp = "\\d{17}", message = "Invalid Steam ID format")
            String steamId) {

        try {
            log.info("Dashboard cache invalidation request: steamId={}", steamId);

            validateSteamIdFormat(steamId);

            dashboardService.invalidateDashboard(steamId);

            log.info("Dashboard cache invalidation successful: steamId={}", steamId);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Dashboard cache invalidation error: steamId={}", steamId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Check cache status
     */
    @GetMapping("/{steamId}/cache/status")
    public ResponseEntity<CacheStatusResponse> getCacheStatus(
            @PathVariable
            @Pattern(regexp = "\\d{17}", message = "Invalid Steam ID format")
            String steamId) {

        try {
            validateSteamIdFormat(steamId);

            boolean isCached = dashboardService.isDashboardCached(steamId);

            CacheStatusResponse response = new CacheStatusResponse(
                    steamId, isCached, isCached ? "Cached" : "Not cached"
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Cache status check error: steamId={}", steamId, e);

            CacheStatusResponse errorResponse = new CacheStatusResponse(
                    steamId, false, "Status check failed"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // Steam ID format validation
    private void validateSteamIdFormat(String steamId) {
        if (steamId == null || steamId.trim().isEmpty()) {
            throw new IllegalArgumentException("Steam ID is required");
        }

        if (!steamId.matches("\\d{17}")) {
            throw new IllegalArgumentException("Steam ID must be 17 digits");
        }

        // Steam ID range validation (minimum value check)
        try {
            long steamIdLong = Long.parseLong(steamId);
            if (steamIdLong < 76561197960265728L) { // Steam ID minimum value
                throw new IllegalArgumentException("Invalid Steam ID range");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Steam ID must be numeric");
        }
    }

    // Error-specific dashboard creation methods
    private DashboardDTO createErrorDashboardForSteamApi(String steamId, SteamApiException e) {
        String errorMessage = switch (e.getStatusCode()) {
            case 403 -> "Steam profile is set to private";
            case 404 -> "Steam user does not exist";
            case 429 -> "Temporarily limited due to too many requests";
            default -> "Problem occurred with Steam API connection";
        };

        return createBasicErrorDashboard(steamId, "STEAM_API_ERROR",
                "Steam Connection Error", errorMessage);
    }

    private DashboardDTO createErrorDashboardForDashboard(String steamId, DashboardException e) {
        String errorMessage = switch (e.getErrorType()) {
            case STEAM_API_ERROR -> "Problem occurred with Steam API connection";
            case DATABASE_ERROR -> "Problem occurred while retrieving game data";
            case CACHE_ERROR -> "Problem occurred with cache system";
            case DATA_PROCESSING_ERROR -> "Problem occurred during data processing";
            default -> "Temporary system problem occurred";
        };

        return createBasicErrorDashboard(steamId, "DASHBOARD_ERROR",
                "Dashboard Generation Error", errorMessage);
    }

    private DashboardDTO createGenericErrorDashboard(String steamId) {
        return createBasicErrorDashboard(steamId, "UNKNOWN_ERROR",
                "Unexpected Error", "An unexpected system problem occurred");
    }

    private DashboardDTO createBasicErrorDashboard(String steamId, String errorType,
                                                   String title, String description) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "Error", BigDecimal.ZERO),
                List.of(),
                List.of(),
                List.of(),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()),
                List.of(new RecommendationInsightDTO(
                        errorType,
                        title,
                        description,
                        "Try Again",
                        "#"
                ))
        );
    }

    // Cache status response DTO
    public static class CacheStatusResponse {
        private String steamId;
        private boolean cached;
        private String status;

        public CacheStatusResponse(String steamId, boolean cached, String status) {
            this.steamId = steamId;
            this.cached = cached;
            this.status = status;
        }

        public String getSteamId() { return steamId; }
        public boolean isCached() { return cached; }
        public String getStatus() { return status; }
    }
}