package SteamGame.recommend.controller;

import SteamGame.recommend.config.RateLimitProperties;
import SteamGame.recommend.config.SecurityConfig;
import SteamGame.recommend.domain.event.entity.FunnelEventType;
import SteamGame.recommend.domain.event.entity.ShareChannel;
import SteamGame.recommend.domain.event.service.FunnelEventService;
import SteamGame.recommend.infra.web.ClientIpResolver;
import SteamGame.recommend.infra.web.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import({SecurityConfig.class, RateLimitFilter.class, RateLimitProperties.class, ClientIpResolver.class})
class EventControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean FunnelEventService eventService;

    private void postEvent(String body, int expected) throws Exception {
        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected));
    }

    @Test
    void validEvent_202_andRecorded() throws Exception {
        postEvent("{\"type\":\"SHARE_CLICK\",\"ref\":\"GMHS\",\"channel\":\"LINK\",\"clientId\":\"c1\"}", 202);

        verify(eventService).validate(FunnelEventType.SHARE_CLICK, "GMHS", ShareChannel.LINK);
        verify(eventService).record("c1", FunnelEventType.SHARE_CLICK, "GMHS", ShareChannel.LINK);
    }

    @Test
    void invalidCombination_400_notRecorded() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EVENT"))
                .when(eventService).validate(any(), any(), any());

        postEvent("{\"type\":\"TEST_COMPLETE\"}", 400);

        verify(eventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void missingType_400() throws Exception {
        postEvent("{\"ref\":\"GMHS\"}", 400);

        verify(eventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void refOver32Chars_400() throws Exception {
        postEvent("{\"type\":\"SHARE_INFLOW\",\"ref\":\"" + "A".repeat(33) + "\"}", 400);

        verify(eventService, never()).record(any(), any(), any(), any());
    }
}
