package SteamGame.recommend.domain.event.service;

import SteamGame.recommend.domain.event.entity.FunnelEvent;
import SteamGame.recommend.domain.event.entity.FunnelEventType;
import SteamGame.recommend.domain.event.entity.ShareChannel;
import SteamGame.recommend.domain.event.repository.FunnelEventRepository;
import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FunnelEventServiceTest {

    FunnelEventRepository repository = mock(FunnelEventRepository.class);
    FunnelEventService service;

    @BeforeEach
    void setUp() {
        service = new FunnelEventService(repository, new TasteContentLoader(new ObjectMapper()));
    }

    private void assertInvalid(FunnelEventType type, String ref, ShareChannel channel) {
        assertThatThrownBy(() -> service.validate(type, ref, channel))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo("INVALID_EVENT");
                });
    }

    @Test
    void landingVisit() {
        assertThatCode(() -> service.validate(FunnelEventType.LANDING_VISIT, null, null)).doesNotThrowAnyException();
        assertInvalid(FunnelEventType.LANDING_VISIT, "TASTE", null);
    }

    @Test
    void featureSelect() {
        assertThatCode(() -> service.validate(FunnelEventType.FEATURE_SELECT, "SIMILAR", null)).doesNotThrowAnyException();
        assertInvalid(FunnelEventType.FEATURE_SELECT, "GMHS", null);
    }

    @Test
    void testStart() {
        assertThatCode(() -> service.validate(FunnelEventType.TEST_START, null, null)).doesNotThrowAnyException();
        assertInvalid(FunnelEventType.TEST_START, "TASTE", null);
    }

    @Test
    void testCompleteIsNotAcceptedFromClient() {
        assertInvalid(FunnelEventType.TEST_COMPLETE, "GMHS", null);
    }

    @Test
    void shareClick() {
        assertThatCode(() -> service.validate(FunnelEventType.SHARE_CLICK, "gmhs", ShareChannel.LINK)).doesNotThrowAnyException();
        assertInvalid(FunnelEventType.SHARE_CLICK, "GMHS", null);
        assertInvalid(FunnelEventType.SHARE_CLICK, "XXXX", ShareChannel.IMAGE);
    }

    @Test
    void shareInflow() {
        assertThatCode(() -> service.validate(FunnelEventType.SHARE_INFLOW, "ENCP", null)).doesNotThrowAnyException();
        assertInvalid(FunnelEventType.SHARE_INFLOW, "ENCP", ShareChannel.LINK);
    }

    @Test
    void record_swallowsRepositoryFailure() {
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));
        assertThatCode(() -> service.record("c1", FunnelEventType.LANDING_VISIT, null, null)).doesNotThrowAnyException();
    }

    @Test
    void record_storesUppercaseTypeCode() {
        service.record("c1", FunnelEventType.SHARE_CLICK, "gmhs", ShareChannel.LINK);
        ArgumentCaptor<FunnelEvent> captor = ArgumentCaptor.forClass(FunnelEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRef()).isEqualTo("GMHS");
        assertThat(captor.getValue().getClientId()).isEqualTo("c1");
        assertThat(captor.getValue().getChannel()).isEqualTo(ShareChannel.LINK);
    }
}
