package SteamGame.recommend.controller;

import SteamGame.recommend.config.RateLimitProperties;
import SteamGame.recommend.config.SecurityConfig;
import SteamGame.recommend.domain.feedback.dto.FeedbackRequestDTO;
import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.feedback.entity.FeedbackSignal;
import SteamGame.recommend.domain.feedback.service.FeedbackService;
import SteamGame.recommend.infra.web.ClientIpResolver;
import SteamGame.recommend.infra.web.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeedbackController.class)
@Import({SecurityConfig.class, RateLimitFilter.class, RateLimitProperties.class, ClientIpResolver.class})
class FeedbackControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean FeedbackService feedbackService;

    @Test
    void validFeedback_returns204_andRecords() throws Exception {
        mockMvc.perform(post("/api/recommend/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"c1\",\"appid\":570,\"context\":\"INPUT\",\"signal\":\"UP\",\"rankPosition\":0}"))
                .andExpect(status().isNoContent());

        verify(feedbackService).record(any());
    }

    @Test
    void clickWithSteamId_deserializesAndRecords() throws Exception {
        mockMvc.perform(post("/api/recommend/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"c1\",\"steamId\":\"765\",\"appid\":570,\"context\":\"PROFILE\",\"signal\":\"CLICK\",\"rankPosition\":2}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<FeedbackRequestDTO> captor = ArgumentCaptor.forClass(FeedbackRequestDTO.class);
        verify(feedbackService).record(captor.capture());
        FeedbackRequestDTO dto = captor.getValue();
        assertThat(dto.getSignal()).isEqualTo(FeedbackSignal.CLICK);
        assertThat(dto.getContext()).isEqualTo(FeedbackContext.PROFILE);
        assertThat(dto.getSteamId()).isEqualTo("765");
        assertThat(dto.getRankPosition()).isEqualTo(2);
        assertThat(dto.getAppid()).isEqualTo(570L);
    }

    @Test
    void matchContext_returns204() throws Exception {
        mockMvc.perform(post("/api/recommend/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"c1\",\"appid\":570,\"context\":\"MATCH\",\"signal\":\"UP\",\"rankPosition\":0}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<FeedbackRequestDTO> captor = ArgumentCaptor.forClass(FeedbackRequestDTO.class);
        verify(feedbackService).record(captor.capture());
        assertThat(captor.getValue().getContext()).isEqualTo(FeedbackContext.MATCH);
    }

    @Test
    void invalidSignal_returns400() throws Exception {
        mockMvc.perform(post("/api/recommend/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"c1\",\"appid\":570,\"context\":\"INPUT\",\"signal\":\"LOL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingClientId_returns400() throws Exception {
        mockMvc.perform(post("/api/recommend/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appid\":570,\"context\":\"INPUT\",\"signal\":\"UP\"}"))
                .andExpect(status().isBadRequest());
    }
}
