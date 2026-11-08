package SteamGame.recommend.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static MockHttpServletRequest requestWithRoute(String route) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, route);
        return request;
    }

    private static ObjectProvider<RecentErrors> providerOf(RecentErrors recorder) {
        return new ObjectProvider<RecentErrors>() {
            @Override
            public RecentErrors getObject() {
                return recorder;
            }
        };
    }

    private static ObjectProvider<RecentErrors> failingProvider() {
        return new ObjectProvider<RecentErrors>() {
            @Override
            public RecentErrors getObject() {
                throw new IllegalStateException("기록 실패");
            }
        };
    }

    @Test
    void responseStatusException_keepsItsStatusAndReason() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> res = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not generate enough game rounds"),
                requestWithRoute("/api/taste/next"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getErrorCode()).isEqualTo("NOT_FOUND");
        assertThat(res.getBody().getMessage()).isEqualTo("Could not generate enough game rounds");
    }

    @Test
    void responseStatusException_withoutReason_usesStatusPhrase() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> res = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_REQUEST), requestWithRoute("/api/taste/next"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getMessage()).isEqualTo("Bad Request");
    }

    @Test
    void responseStatusException_5xx_masksReason() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> res = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Groq API 오류"), requestWithRoute("/api/taste/next"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getMessage()).doesNotContain("Groq");
    }

    private List<ILoggingEvent> capture(ResponseStatusException e) {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            handler.handleResponseStatus(e, requestWithRoute("/api/taste/next"));
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list;
    }

    // 스팀 장애 때 분석 요청마다 스택이 ERROR로 쌓이면 진짜 키 문제 로그가 묻힌다
    @Test
    void responseStatusException_503_logsWarnWithoutStack() {
        List<ILoggingEvent> events = capture(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Steam down"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(events.get(0).getThrowableProxy()).isNull();
        assertThat(events.get(0).getFormattedMessage()).startsWith("Response status error: 503").contains("Steam down");
    }

    @Test
    void responseStatusException_500_logsErrorWithStack() {
        List<ILoggingEvent> events = capture(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "boom"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(events.get(0).getThrowableProxy()).isNotNull();
    }

    // 아래는 기록기 연결을 확인함

    @Test
    void responseStatusException_기록기가_있으면_상태와_코드와_경로를_남긴다() {
        RecentErrors recorder = new RecentErrors();
        GlobalExceptionHandler h = new GlobalExceptionHandler(providerOf(recorder));

        h.handleResponseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND, "없음"),
                requestWithRoute("/api/taste/next"));

        RecentErrors.Entry entry = recorder.snapshot().recent().get(0);
        assertThat(entry.status()).isEqualTo(404);
        assertThat(entry.errorCode()).isEqualTo("NOT_FOUND");
        assertThat(entry.route()).isEqualTo("/api/taste/next");
    }

    @Test
    void responseStatusException_경로패턴_속성이_없으면_unmatched로_남긴다() {
        RecentErrors recorder = new RecentErrors();
        GlobalExceptionHandler h = new GlobalExceptionHandler(providerOf(recorder));

        h.handleResponseStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST), new MockHttpServletRequest());

        RecentErrors.Entry entry = recorder.snapshot().recent().get(0);
        assertThat(entry.route()).isEqualTo("(unmatched)");
    }

    @Test
    void steamApiException_기록기가_있으면_매핑된_상태로_남긴다() {
        RecentErrors recorder = new RecentErrors();
        GlobalExceptionHandler h = new GlobalExceptionHandler(providerOf(recorder));

        h.handleSteamApiException(new SteamApiException("boom", "7656100", 503), requestWithRoute("/api/library/{steamId}"));

        RecentErrors.Entry entry = recorder.snapshot().recent().get(0);
        assertThat(entry.status()).isEqualTo(502);
        assertThat(entry.errorCode()).isEqualTo("STEAM_API_ERROR");
        assertThat(entry.route()).isEqualTo("/api/library/{steamId}");
    }

    @Test
    void dashboardException_기록기가_있으면_상태와_코드를_남긴다() {
        RecentErrors recorder = new RecentErrors();
        GlobalExceptionHandler h = new GlobalExceptionHandler(providerOf(recorder));

        h.handleDashboardException(
                new DashboardException("boom", "7656100", DashboardException.ErrorType.DATABASE_ERROR),
                requestWithRoute("/api/metrics/dashboard"));

        RecentErrors.Entry entry = recorder.snapshot().recent().get(0);
        assertThat(entry.status()).isEqualTo(500);
        assertThat(entry.errorCode()).isEqualTo("DASHBOARD_ERROR");
    }

    @Test
    void gameDataException_기록기가_있으면_400으로_남긴다() {
        RecentErrors recorder = new RecentErrors();
        GlobalExceptionHandler h = new GlobalExceptionHandler(providerOf(recorder));

        h.handleGameDataException(new GameDataException("boom", 1L), requestWithRoute("/api/game/{appId}"));

        RecentErrors.Entry entry = recorder.snapshot().recent().get(0);
        assertThat(entry.status()).isEqualTo(400);
        assertThat(entry.errorCode()).isEqualTo("GAME_DATA_ERROR");
    }

    @Test
    void validation_기록기가_있으면_400으로_남긴다() {
        RecentErrors recorder = new RecentErrors();
        GlobalExceptionHandler h = new GlobalExceptionHandler(providerOf(recorder));

        h.handleValidation(new ConstraintViolationException(Set.of()), requestWithRoute("/api/taste/answer"));

        RecentErrors.Entry entry = recorder.snapshot().recent().get(0);
        assertThat(entry.status()).isEqualTo(400);
        assertThat(entry.errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void genericException_기록기가_있으면_500으로_남긴다() {
        RecentErrors recorder = new RecentErrors();
        GlobalExceptionHandler h = new GlobalExceptionHandler(providerOf(recorder));

        h.handleGenericException(new RuntimeException("boom"), requestWithRoute("/api/unknown"));

        RecentErrors.Entry entry = recorder.snapshot().recent().get(0);
        assertThat(entry.status()).isEqualTo(500);
        assertThat(entry.errorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    void noResourceFound_경로없음은_횟수만_올리고_목록에는_안_남긴다() throws Exception {
        RecentErrors recorder = new RecentErrors();
        GlobalExceptionHandler h = new GlobalExceptionHandler(providerOf(recorder));

        h.handleNoResourceFound(new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/no-such-route"),
                requestWithRoute("/no-such-route"));

        RecentErrors.Snapshot snapshot = recorder.snapshot();
        assertThat(snapshot.recent()).isEmpty();
        assertThat(snapshot.counts()).hasSize(1);
        assertThat(snapshot.counts().get(0).status()).isEqualTo(404);
        assertThat(snapshot.counts().get(0).errorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void noResourceFound_favicon은_기록을_남기지_않는다() throws Exception {
        RecentErrors recorder = new RecentErrors();
        GlobalExceptionHandler h = new GlobalExceptionHandler(providerOf(recorder));

        h.handleNoResourceFound(new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/favicon.ico"),
                requestWithRoute("/favicon.ico"));

        RecentErrors.Snapshot snapshot = recorder.snapshot();
        assertThat(snapshot.recent()).isEmpty();
        assertThat(snapshot.counts()).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/a.jpeg", "/a.gif", "/a.svg", "/a.webp", "/a.avif", "/a.bmp", "/A.PNG", "/FAVICON.ICO"})
    void noResourceFound_정적_파일은_대소문자와_상관없이_세지_않는다(String path) throws Exception {
        RecentErrors recorder = new RecentErrors();
        GlobalExceptionHandler h = new GlobalExceptionHandler(providerOf(recorder));

        ResponseEntity<Void> res = h.handleNoResourceFound(
                new NoResourceFoundException(org.springframework.http.HttpMethod.GET, path), requestWithRoute(path));

        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(recorder.snapshot().counts()).isEmpty();
    }

    @Test
    void 기록기가_없어도_응답은_그대로다() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> res = h.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "없음"), requestWithRoute("/api/taste/next"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getErrorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void 기록기가_던지는_예외는_응답에_영향을_주지_않는다() {
        GlobalExceptionHandler h = new GlobalExceptionHandler(failingProvider());

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> res = h.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "없음"), requestWithRoute("/api/taste/next"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).isNotNull();
    }
}
