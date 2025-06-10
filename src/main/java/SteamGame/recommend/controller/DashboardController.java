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
     * 사용자 대시보드 조회
     */
    @GetMapping("/{steamId}")
    public ResponseEntity<DashboardDTO> getDashboard(
            @PathVariable
            @Pattern(regexp = "\\d{17}", message = "유효하지 않은 Steam ID 형식입니다")
            String steamId) {

        try {
            log.info("대시보드 조회 요청: steamId={}", steamId);

            // 🔧 Steam ID 추가 검증
            validateSteamIdFormat(steamId);

            DashboardDTO dashboard = dashboardService.generateDashboard(steamId);

            log.info("대시보드 조회 성공: steamId={}", steamId);
            return ResponseEntity.ok(dashboard);

        } catch (SteamApiException e) {
            log.warn("Steam API 오류로 인한 대시보드 조회 실패: steamId={}, message={}",
                    steamId, e.getMessage());

            // Steam API 오류 시에도 기본 대시보드는 반환
            DashboardDTO errorDashboard = createErrorDashboardForSteamApi(steamId, e);
            return ResponseEntity.status(HttpStatus.OK).body(errorDashboard);

        } catch (DashboardException e) {
            log.error("대시보드 생성 오류: steamId={}, type={}, message={}",
                    steamId, e.getErrorType(), e.getMessage());

            // 대시보드 생성 오류도 기본 대시보드 반환
            DashboardDTO errorDashboard = createErrorDashboardForDashboard(steamId, e);
            return ResponseEntity.status(HttpStatus.OK).body(errorDashboard);

        } catch (Exception e) {
            log.error("예상치 못한 대시보드 조회 오류: steamId={}", steamId, e);

            // 예상치 못한 오류도 기본 대시보드 반환
            DashboardDTO errorDashboard = createGenericErrorDashboard(steamId);
            return ResponseEntity.status(HttpStatus.OK).body(errorDashboard);
        }
    }

    /**
     * 대시보드 강제 새로고침
     */
    @PostMapping("/{steamId}/refresh")
    public ResponseEntity<DashboardDTO> refreshDashboard(
            @PathVariable
            @Pattern(regexp = "\\d{17}", message = "유효하지 않은 Steam ID 형식입니다")
            String steamId) {

        try {
            log.info("대시보드 강제 새로고침 요청: steamId={}", steamId);

            validateSteamIdFormat(steamId);

            DashboardDTO dashboard = dashboardService.refreshDashboard(steamId);

            log.info("대시보드 강제 새로고침 성공: steamId={}", steamId);
            return ResponseEntity.ok(dashboard);

        } catch (Exception e) {
            log.error("대시보드 강제 새로고침 오류: steamId={}", steamId, e);

            // 새로고침 실패 시 기존 캐시된 데이터라도 반환 시도
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
     * 대시보드 캐시 무효화
     */
    @DeleteMapping("/{steamId}/cache")
    public ResponseEntity<Void> invalidateCache(
            @PathVariable
            @Pattern(regexp = "\\d{17}", message = "유효하지 않은 Steam ID 형식입니다")
            String steamId) {

        try {
            log.info("대시보드 캐시 무효화 요청: steamId={}", steamId);

            validateSteamIdFormat(steamId);

            dashboardService.invalidateDashboard(steamId);

            log.info("대시보드 캐시 무효화 성공: steamId={}", steamId);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("대시보드 캐시 무효화 오류: steamId={}", steamId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 캐시 상태 확인
     */
    @GetMapping("/{steamId}/cache/status")
    public ResponseEntity<CacheStatusResponse> getCacheStatus(
            @PathVariable
            @Pattern(regexp = "\\d{17}", message = "유효하지 않은 Steam ID 형식입니다")
            String steamId) {

        try {
            validateSteamIdFormat(steamId);

            boolean isCached = dashboardService.isDashboardCached(steamId);

            CacheStatusResponse response = new CacheStatusResponse(
                    steamId, isCached, isCached ? "캐시됨" : "캐시 없음"
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("캐시 상태 확인 오류: steamId={}", steamId, e);

            CacheStatusResponse errorResponse = new CacheStatusResponse(
                    steamId, false, "상태 확인 실패"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // 🔧 Steam ID 형식 검증
    private void validateSteamIdFormat(String steamId) {
        if (steamId == null || steamId.trim().isEmpty()) {
            throw new IllegalArgumentException("Steam ID가 필요합니다");
        }

        if (!steamId.matches("\\d{17}")) {
            throw new IllegalArgumentException("Steam ID는 17자리 숫자여야 합니다");
        }

        // Steam ID 범위 검증 (최소값 체크)
        try {
            long steamIdLong = Long.parseLong(steamId);
            if (steamIdLong < 76561197960265728L) { // Steam ID 최소값
                throw new IllegalArgumentException("유효하지 않은 Steam ID 범위입니다");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Steam ID는 숫자여야 합니다");
        }
    }

    // 🔧 에러별 대시보드 생성 메서드들
    private DashboardDTO createErrorDashboardForSteamApi(String steamId, SteamApiException e) {
        String errorMessage = switch (e.getStatusCode()) {
            case 403 -> "Steam 프로필이 비공개로 설정되어 있습니다";
            case 404 -> "존재하지 않는 Steam 사용자입니다";
            case 429 -> "너무 많은 요청으로 일시적으로 제한되었습니다";
            default -> "Steam API 연결에 문제가 발생했습니다";
        };

        return createBasicErrorDashboard(steamId, "STEAM_API_ERROR",
                "Steam 연결 오류", errorMessage);
    }

    private DashboardDTO createErrorDashboardForDashboard(String steamId, DashboardException e) {
        String errorMessage = switch (e.getErrorType()) {
            case STEAM_API_ERROR -> "Steam API 연결에 문제가 발생했습니다";
            case DATABASE_ERROR -> "게임 데이터 조회에 문제가 발생했습니다";
            case CACHE_ERROR -> "캐시 시스템에 문제가 발생했습니다";
            case DATA_PROCESSING_ERROR -> "데이터 처리 중 문제가 발생했습니다";
            default -> "시스템에 일시적인 문제가 발생했습니다";
        };

        return createBasicErrorDashboard(steamId, "DASHBOARD_ERROR",
                "대시보드 생성 오류", errorMessage);
    }

    private DashboardDTO createGenericErrorDashboard(String steamId) {
        return createBasicErrorDashboard(steamId, "UNKNOWN_ERROR",
                "예상치 못한 오류", "시스템에 예상치 못한 문제가 발생했습니다");
    }

    private DashboardDTO createBasicErrorDashboard(String steamId, String errorType,
                                                   String title, String description) {
        return new DashboardDTO(
                steamId,
                new UserProfileSummary(0, BigDecimal.ZERO, 0, "오류", BigDecimal.ZERO),
                List.of(),
                List.of(),
                List.of(),
                new GameLibraryStatsDTO(0, 0, 0, 0, List.of(), List.of()),
                List.of(new RecommendationInsightDTO(
                        errorType,
                        title,
                        description,
                        "다시 시도",
                        "#"
                ))
        );
    }

    // 🔧 캐시 상태 응답 DTO
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