package SteamGame.recommend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

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
}
