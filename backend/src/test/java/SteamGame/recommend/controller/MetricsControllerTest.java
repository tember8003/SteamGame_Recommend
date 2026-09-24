package SteamGame.recommend.controller;

import SteamGame.recommend.domain.metrics.dto.MetricsOverviewDTO;
import SteamGame.recommend.domain.metrics.service.MetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MetricsControllerTest {

    private final MetricsService service = mock(MetricsService.class);

    private MetricsOverviewDTO sample() {
        return new MetricsOverviewDTO(0L, List.of(), List.of());
    }

    @Test
    void overview_withMatchingToken_returns200() {
        MetricsController controller = new MetricsController(service, "secret");
        when(service.overview(anyInt())).thenReturn(sample());

        ResponseEntity<MetricsOverviewDTO> res = controller.overview("secret");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
    }

    @Test
    void overview_withWrongToken_returns401() {
        MetricsController controller = new MetricsController(service, "secret");

        ResponseEntity<MetricsOverviewDTO> res = controller.overview("nope");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }

    @Test
    void overview_withMissingToken_returns401() {
        MetricsController controller = new MetricsController(service, "secret");

        ResponseEntity<MetricsOverviewDTO> res = controller.overview(null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }

    @Test
    void overview_withBlankConfiguredToken_returns401() {
        MetricsController controller = new MetricsController(service, "");

        ResponseEntity<MetricsOverviewDTO> res = controller.overview("secret");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }
}
