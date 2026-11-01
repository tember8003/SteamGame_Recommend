package SteamGame.recommend.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void responseStatusException_keepsItsStatusAndReason() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> res = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not generate enough game rounds"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getErrorCode()).isEqualTo("NOT_FOUND");
        assertThat(res.getBody().getMessage()).isEqualTo("Could not generate enough game rounds");
    }

    @Test
    void responseStatusException_withoutReason_usesStatusPhrase() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> res = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_REQUEST));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getMessage()).isEqualTo("Bad Request");
    }

    @Test
    void responseStatusException_5xx_masksReason() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> res = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Groq API 오류"));

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
            handler.handleResponseStatus(e);
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
}
