package SteamGame.recommend.infra.web;

import SteamGame.recommend.config.RateLimitProperties;
import SteamGame.recommend.config.SecurityConfig;
import SteamGame.recommend.controller.LibraryController;
import SteamGame.recommend.domain.event.service.FunnelEventService;
import SteamGame.recommend.domain.library.LibraryAnalysisService;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis;
import SteamGame.recommend.domain.library.analysis.LibraryAnalyzer;
import SteamGame.recommend.domain.library.content.LibraryContentLoader;
import SteamGame.recommend.domain.library.steam.OwnedGame;
import SteamGame.recommend.domain.library.steam.SteamProfile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 라이브러리 분석 10번, 공유 저장 20번 제한, 테스트마다 다른 IP를 써서 버킷이 섞이지 않게 한다
@WebMvcTest(LibraryController.class)
@Import({SecurityConfig.class, RateLimitFilter.class, RateLimitProperties.class, ClientIpResolver.class,
        LibraryContentLoader.class})
class RateLimitLibraryTest {

    @Autowired MockMvc mvc;
    @Autowired LibraryContentLoader content;
    @MockitoBean LibraryAnalysisService service;
    @MockitoBean FunnelEventService eventService;

    private MockHttpServletRequestBuilder postFrom(String path, String ip, String body) {
        return post(path)
                .with(req -> { req.setRemoteAddr(ip); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void assertLimitedAfter(String path, String ip, String body, int capacity) throws Exception {
        for (int i = 0; i < capacity; i++) {
            int code = mvc.perform(postFrom(path, ip, body)).andReturn().getResponse().getStatus();
            assertThat(code).as("request %d", i + 1).isNotEqualTo(429);
        }
        mvc.perform(postFrom(path, ip, body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("rate_limited"));
        int other = mvc.perform(postFrom(path, ip + "9", body)).andReturn().getResponse().getStatus();
        assertThat(other).isNotEqualTo(429);
    }

    @Test
    void analyze_first10AreOk_11th429() throws Exception {
        String tag = content.content().tagGroups().get("STRATEGY").get(0);
        LibraryAnalysis analysis = LibraryAnalyzer.analyze(
                List.of(new OwnedGame(1, "G", 600, 0, null)), Map.of(1L, List.of(tag)), t -> 0.5,
                content.content(), "76561198012345678");
        when(service.analyze("abc")).thenReturn(new LibraryAnalysisService.Analyzed("aid",
                new SteamProfile("76561198012345678", "n", "u"), analysis));
        for (int i = 0; i < 10; i++) {
            assertThat(mvc.perform(postFrom("/api/library/analyze", "10.2.0.1", "{\"steamId\":\"abc\"}"))
                    .andReturn().getResponse().getStatus()).as("request %d", i + 1).isEqualTo(200);
        }
        mvc.perform(postFrom("/api/library/analyze", "10.2.0.1", "{\"steamId\":\"abc\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void share_21stRequest_429() throws Exception {
        assertLimitedAfter("/api/library/share", "10.2.0.2", "{\"analysisId\":\"abc\"}", 20);
    }

    @Test
    void getShare_isNotLimited() throws Exception {
        for (int i = 0; i < 40; i++) {
            int code = mvc.perform(get("/api/library/share/abc").with(req -> { req.setRemoteAddr("10.2.0.3"); return req; }))
                    .andReturn().getResponse().getStatus();
            assertThat(code).isNotEqualTo(429);
        }
    }
}
