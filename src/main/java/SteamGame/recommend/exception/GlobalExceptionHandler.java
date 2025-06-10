package SteamGame.recommend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Steam API 관련 예외 처리
     */
    @ExceptionHandler(SteamApiException.class)
    public ResponseEntity<ErrorResponse> handleSteamApiException(SteamApiException e) {
        log.error("Steam API 오류: steamId={}, statusCode={}, message={}",
                e.getSteamId(), e.getStatusCode(), e.getMessage(), e);

        HttpStatus httpStatus = mapSteamApiStatusToHttp(e.getStatusCode());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("STEAM_API_ERROR")
                .message(e.getMessage())
                .timestamp(LocalDateTime.now())
                .details(Map.of(
                        "steamId", e.getSteamId(),
                        "steamApiStatusCode", e.getStatusCode()
                ))
                .build();

        return ResponseEntity.status(httpStatus).body(errorResponse);
    }

    /**
     * 대시보드 관련 예외 처리
     */
    @ExceptionHandler(DashboardException.class)
    public ResponseEntity<ErrorResponse> handleDashboardException(DashboardException e) {
        log.error("대시보드 오류: steamId={}, type={}, message={}",
                e.getSteamId(), e.getErrorType(), e.getMessage(), e);

        HttpStatus httpStatus = mapDashboardErrorToHttp(e.getErrorType());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("DASHBOARD_ERROR")
                .message(e.getMessage())
                .timestamp(LocalDateTime.now())
                .details(Map.of(
                        "steamId", e.getSteamId(),
                        "errorType", e.getErrorType().name(),
                        "errorDescription", e.getErrorType().getDescription()
                ))
                .build();

        return ResponseEntity.status(httpStatus).body(errorResponse);
    }

    /**
     * 게임 데이터 관련 예외 처리
     */
    @ExceptionHandler(GameDataException.class)
    public ResponseEntity<ErrorResponse> handleGameDataException(GameDataException e) {
        log.error("게임 데이터 오류: appId={}, message={}", e.getAppId(), e.getMessage(), e);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("GAME_DATA_ERROR")
                .message(e.getMessage())
                .timestamp(LocalDateTime.now())
                .details(Map.of("appId", e.getAppId()))
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 일반적인 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("예상치 못한 시스템 오류", e);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("시스템에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.")
                .timestamp(LocalDateTime.now())
                .details(Map.of("exceptionType", e.getClass().getSimpleName()))
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Steam API 상태 코드를 HTTP 상태로 매핑
     */
    private HttpStatus mapSteamApiStatusToHttp(int steamApiStatusCode) {
        return switch (steamApiStatusCode) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 500, 502, 503 -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * 대시보드 에러 타입을 HTTP 상태로 매핑
     */
    private HttpStatus mapDashboardErrorToHttp(DashboardException.ErrorType errorType) {
        return switch (errorType) {
            case STEAM_API_ERROR -> HttpStatus.BAD_GATEWAY;
            case DATABASE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case CACHE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case DATA_PROCESSING_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case UNKNOWN_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * 에러 응답 DTO
     */
    public static class ErrorResponse {
        private String errorCode;
        private String message;
        private LocalDateTime timestamp;
        private Map<String, Object> details;

        // 🔧 Builder 패턴 구현
        public static ErrorResponseBuilder builder() {
            return new ErrorResponseBuilder();
        }

        public static class ErrorResponseBuilder {
            private String errorCode;
            private String message;
            private LocalDateTime timestamp;
            private Map<String, Object> details = new HashMap<>();

            public ErrorResponseBuilder errorCode(String errorCode) {
                this.errorCode = errorCode;
                return this;
            }

            public ErrorResponseBuilder message(String message) {
                this.message = message;
                return this;
            }

            public ErrorResponseBuilder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public ErrorResponseBuilder details(Map<String, Object> details) {
                this.details = details;
                return this;
            }

            public ErrorResponse build() {
                ErrorResponse response = new ErrorResponse();
                response.errorCode = this.errorCode;
                response.message = this.message;
                response.timestamp = this.timestamp;
                response.details = this.details;
                return response;
            }
        }

        // Getters
        public String getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Map<String, Object> getDetails() { return details; }
    }
}