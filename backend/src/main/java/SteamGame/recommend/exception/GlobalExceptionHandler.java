package SteamGame.recommend.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 브라우저와 봇이 자동으로 찾는 정적 파일이다, 404 횟수가 API 경로 없음을 가리키도록 세지 않음
    private static final List<String> STATIC_ASSET_MARKERS = List.of(
            "favicon", ".ico", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".avif", ".bmp");

    // RecentErrors 빈이 없는 @WebMvcTest 에서도 이 클래스는 그대로 뜨므로 RecentErrors 를 바로 받지 않고 ObjectProvider 로 받는다
    private final ObjectProvider<RecentErrors> recentErrors;

    @Autowired
    public GlobalExceptionHandler(ObjectProvider<RecentErrors> recentErrors) {
        this.recentErrors = recentErrors;
    }

    // 기록기 없이 직접 생성하던 기존 단위 테스트 방식도 그대로 동작해야 해서 둔다
    public GlobalExceptionHandler() {
        this(new ObjectProvider<RecentErrors>() {
            @Override
            public RecentErrors getObject() {
                return null;
            }
        });
    }

    //favicon exception
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        // 대소문자를 가리지 않고 거른다, /FAVICON.ICO 같은 요청도 정적 파일이다
        String path = ex.getResourcePath().toLowerCase(Locale.ROOT);
        if (STATIC_ASSET_MARKERS.stream().anyMatch(path::contains)) {
            return ResponseEntity.notFound().build();
        }

        log.warn("API resource not found: {}", ex.getResourcePath());
        // 봇이 임의 주소를 찔러 보는 경우가 많아 횟수만 센다, 목록에는 넣지 않는다
        safeCount(404, "NOT_FOUND");
        return ResponseEntity.notFound().build();
    }

    //Steam API exception
    @ExceptionHandler(SteamApiException.class)
    public ResponseEntity<ErrorResponse> handleSteamApiException(SteamApiException e, HttpServletRequest request) {
        log.error("Steam API Error: steamId={}, statusCode={}, message={}",
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

        safeRecord(httpStatus.value(), "STEAM_API_ERROR", request);
        return ResponseEntity.status(httpStatus).body(errorResponse);
    }

    //Dashboard exception
    @ExceptionHandler(DashboardException.class)
    public ResponseEntity<ErrorResponse> handleDashboardException(DashboardException e, HttpServletRequest request) {
        log.error("Dashboard Error: steamId={}, type={}, message={}",
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

        safeRecord(httpStatus.value(), "DASHBOARD_ERROR", request);
        return ResponseEntity.status(httpStatus).body(errorResponse);
    }

    //Game data exception
    @ExceptionHandler(GameDataException.class)
    public ResponseEntity<ErrorResponse> handleGameDataException(GameDataException e, HttpServletRequest request) {
        log.error("Game data Error: appId={}, message={}", e.getAppId(), e.getMessage(), e);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("GAME_DATA_ERROR")
                .message(e.getMessage())
                .timestamp(LocalDateTime.now())
                .details(Map.of("appId", e.getAppId()))
                .build();

        safeRecord(HttpStatus.BAD_REQUEST.value(), "GAME_DATA_ERROR", request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    //request validation (@Valid 바디 / 역직렬화 / @Validated 파라미터 위반) → 400
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> handleValidation(Exception e, HttpServletRequest request) {
        log.warn("Validation error: {}", e.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message("Invalid request")
                .timestamp(LocalDateTime.now())
                .details(Map.of("exceptionType", e.getClass().getSimpleName()))
                .build();

        safeRecord(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // 코드가 상태를 정해 던진 예외는 그 상태 그대로 돌려준다, 범용 처리기로 떨어지면 전부 500 이 됨
    // 5xx 사유에는 외부 제공자 이름 같은 내부 정보가 들어 있어 응답에는 일반 문구만 싣고 사유는 로그에만 남긴다
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        String errorCode = status != null ? status.name() : "HTTP_" + e.getStatusCode().value();
        String message;

        if (e.getStatusCode().is5xxServerError()) {
            if (e.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                // 외부 장애 때 요청마다 스택이 ERROR로 쌓이면 키 문제 같은 진짜 오류가 묻혀서 WARN으로 낮춘다
                log.warn("Response status error: {} {}", e.getStatusCode().value(), e.getReason());
            } else {
                log.error("Response status error: {} {}", e.getStatusCode().value(), e.getReason(), e);
            }
            message = "There was a temporary problem with the system, please try again in a moment.";
        } else {
            message = e.getReason() != null ? e.getReason()
                    : (status != null ? status.getReasonPhrase() : errorCode);
            log.warn("Response status error: {} {}", e.getStatusCode().value(), message);
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        safeRecord(e.getStatusCode().value(), errorCode, request);
        return ResponseEntity.status(e.getStatusCode()).body(errorResponse);
    }

    //normal exception
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e, HttpServletRequest request) {
        log.error("unexpected Error: ", e);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("There was a temporary problem with the system, please try again in a moment.")
                .timestamp(LocalDateTime.now())
                .details(Map.of("exceptionType", e.getClass().getSimpleName()))
                .build();

        safeRecord(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_SERVER_ERROR", request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    // 경로 패턴은 요청 속성에서 읽는다, 없으면 매칭 전이라 다룰 패턴이 없다는 뜻이다
    private String routePattern(HttpServletRequest request) {
        if (request == null) {
            return "(unmatched)";
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : "(unmatched)";
    }

    // 기록 중 예외가 나도 오류 응답은 그대로 나가야 해서 실패를 무시한다
    private void safeRecord(int status, String errorCode, HttpServletRequest request) {
        try {
            RecentErrors recorder = recentErrors.getIfAvailable();
            if (recorder != null) {
                recorder.record(status, errorCode, routePattern(request));
            }
        } catch (RuntimeException ignored) {
            // 기록 실패는 응답에 영향을 주지 않는다
        }
    }

    private void safeCount(int status, String errorCode) {
        try {
            RecentErrors recorder = recentErrors.getIfAvailable();
            if (recorder != null) {
                recorder.count(status, errorCode);
            }
        } catch (RuntimeException ignored) {
            // 기록 실패는 응답에 영향을 주지 않는다
        }
    }

    //Mapping Steam API Status Code to HTTP Status
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

    //Mapping Dashboard Error to HTTP Status
    private HttpStatus mapDashboardErrorToHttp(DashboardException.ErrorType errorType) {
        return switch (errorType) {
            case STEAM_API_ERROR -> HttpStatus.BAD_GATEWAY;
            case DATABASE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case CACHE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case DATA_PROCESSING_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case UNKNOWN_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    //Error response DTO
    public static class ErrorResponse {
        private String errorCode;
        private String message;
        private LocalDateTime timestamp;
        private Map<String, Object> details;

        // Builder
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