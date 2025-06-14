package SteamGame.recommend.controller;

import SteamGame.recommend.domain.dashboard.dto.*;
import SteamGame.recommend.domain.dashboard.service.DashboardService;
import SteamGame.recommend.exception.DashboardException;
import SteamGame.recommend.exception.SteamApiException;

import SteamGame.recommend.infra.utils.SteamIdValidator;
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
            SteamIdValidator.isValidSteamId(steamId);

            DashboardDTO dashboard = dashboardService.generateDashboard(steamId);

            log.info("Dashboard retrieval successful: steamId={}", steamId);
            return ResponseEntity.ok(dashboard);

        } catch (SteamApiException e) {
            log.warn("Dashboard retrieval failed due to Steam API error: steamId={}, statusCode={}, message={}",
                    steamId, e.getStatusCode(), e.getMessage());

            // Service에서 이미 에러 처리가 되므로 이 부분은 실행되지 않을 수 있음
            DashboardDTO errorDashboard = createErrorDashboardForSteamApi(steamId, e);
            return ResponseEntity.ok(errorDashboard);

        } catch (DashboardException e) {
            log.error("Dashboard generation error: steamId={}, type={}, message={}",
                    steamId, e.getErrorType(), e.getMessage());

            DashboardDTO errorDashboard = createErrorDashboardForDashboard(steamId, e);
            return ResponseEntity.ok(errorDashboard);

        } catch (Exception e) {
            log.error("Unexpected dashboard retrieval error: steamId={}", steamId, e);

            DashboardDTO errorDashboard = createGenericErrorDashboard(steamId);
            return ResponseEntity.ok(errorDashboard);
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

            SteamIdValidator.isValidSteamId(steamId);

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
                return ResponseEntity.ok(errorDashboard);
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

            SteamIdValidator.isValidSteamId(steamId);

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
            SteamIdValidator.isValidSteamId(steamId);

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

    // Error dashboard
    private DashboardDTO createErrorDashboardForSteamApi(String steamId, SteamApiException e) {
        String errorMessage = switch (e.getStatusCode()) {
            case 403 -> "Steam profile is set to private";
            case 404 -> "Steam user does not exist";
            case 429 -> "Temporarily limited due to too many requests";
            default -> "Problem occurred with Steam API connection";
        };

        String favoriteGenre = switch (e.getStatusCode()) {
            case 403 -> "Private";
            case 404 -> "Not Found";
            case 429 -> "Rate Limited";
            default -> "Error";
        };

        return createBasicErrorDashboard(steamId, favoriteGenre, "STEAM_API_ERROR",
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

        String favoriteGenre = switch (e.getErrorType()) {
            case DATABASE_ERROR -> "DB Error";
            case CACHE_ERROR -> "Cache Error";
            case DATA_PROCESSING_ERROR -> "Processing Error";
            default -> "System Error";
        };

        return createBasicErrorDashboard(steamId, favoriteGenre, "DASHBOARD_ERROR",
                "Dashboard Generation Error", errorMessage);
    }

    private DashboardDTO createGenericErrorDashboard(String steamId) {
        return createBasicErrorDashboard(steamId, "Error", "UNKNOWN_ERROR",
                "Unexpected Error", "An unexpected system problem occurred");
    }

    private DashboardDTO createBasicErrorDashboard(String steamId, String favoriteGenre,
                                                   String errorType, String title, String description) {

        // steamId, profileSummary, topTags, genreDistribution, recentActivity, libraryStats
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, favoriteGenre, BigDecimal.ZERO),
                List.of(),  // topTags
                List.of(),  // genreDistribution
                new Recent2WeeksActivityDTO(BigDecimal.ZERO, 0, BigDecimal.ZERO, null), // recentActivity
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()) // libraryStats
        );
    }
}