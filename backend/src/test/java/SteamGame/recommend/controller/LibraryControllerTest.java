package SteamGame.recommend.controller;

import SteamGame.recommend.config.JacksonConfig;
import SteamGame.recommend.config.RateLimitProperties;
import SteamGame.recommend.config.SecurityConfig;
import SteamGame.recommend.domain.event.entity.FunnelEventType;
import SteamGame.recommend.domain.event.service.FunnelEventService;
import SteamGame.recommend.domain.library.LibraryAnalysisService;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis;
import SteamGame.recommend.domain.library.analysis.LibraryAnalyzer;
import SteamGame.recommend.domain.library.content.LibraryContentLoader;
import SteamGame.recommend.domain.library.dto.LibraryViewMapper;
import SteamGame.recommend.domain.taste.content.Lang;
import SteamGame.recommend.domain.library.steam.OwnedGame;
import SteamGame.recommend.domain.library.steam.SteamProfile;
import SteamGame.recommend.infra.web.ClientIpResolver;
import SteamGame.recommend.infra.web.RateLimitFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LibraryController.class)
@Import({JacksonConfig.class, SecurityConfig.class, RateLimitFilter.class, RateLimitProperties.class, ClientIpResolver.class,
        LibraryContentLoader.class})
class LibraryControllerTest {

    @Autowired MockMvc mvc;
    @Autowired LibraryContentLoader loader;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean LibraryAnalysisService service;
    @MockitoBean FunnelEventService eventService;

    // 요청마다 다른 IP를 써서 분당 10번 제한에 걸리지 않게 한다
    static final java.util.concurrent.atomic.AtomicInteger ipCounter = new java.util.concurrent.atomic.AtomicInteger();

    LibraryAnalysis analysis;
    SteamProfile profile = new SteamProfile("76561198012345678", "Nick", "https://avatars.example/a.jpg");

    @BeforeEach
    void setUp() {
        String tag = loader.content().tagGroups().get("STRATEGY").get(0);
        List<OwnedGame> games = new ArrayList<>();
        Map<Long, List<String>> tags = new java.util.HashMap<>();
        for (long i = 1; i <= 6; i++) {
            games.add(new OwnedGame(i, "Game" + i, (int) (i * 100), i == 1 ? 30 : 0, null));
            tags.put(i, List.of(tag));
        }
        games.add(new OwnedGame(7, "Unplayed", 0, 0, null));
        analysis = LibraryAnalyzer.analyze(games, tags, t -> 0.5, loader.content(), profile.steamId64());
    }

    private static final String BODY = "{\"steamId\":\"76561198012345678\",\"clientId\":\"cid-1\"}";

    private MvcResult analyze(String query, String body) throws Exception {
        String ip = "10.3.0." + ipCounter.incrementAndGet();
        return mvc.perform(post("/api/library/analyze" + query)
                .with(req -> { req.setRemoteAddr(ip); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
    }

    private JsonNode json(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> out = new TreeSet<>();
        node.fieldNames().forEachRemaining(out::add);
        return out;
    }

    @Test
    void analyze_ok_hasContractKeys() throws Exception {
        when(service.analyze("76561198012345678"))
                .thenReturn(new LibraryAnalysisService.Analyzed("aid-1", profile, analysis));

        MvcResult res = analyze("", BODY);

        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(res);
        assertThat(keys(body)).containsExactlyInAnyOrder("analysisId", "limitedData", "profile", "stats", "ledger",
                "verdict", "gamerStats", "diagnosis");
        assertThat(body.get("analysisId").asText()).isEqualTo("aid-1");
        assertThat(keys(body.get("profile"))).containsExactlyInAnyOrder("personaName", "avatarUrl", "steamId64");
        assertThat(body.get("profile").get("steamId64").asText()).isEqualTo("76561198012345678");
        assertThat(keys(body.get("stats"))).containsExactlyInAnyOrder("ownedCount", "totalHours", "zeroPlayCount",
                "zeroPlayRatio", "mostPlayed", "recentGameCount", "recentHours", "matchedCount", "topTags");
        assertThat(keys(body.get("stats").get("mostPlayed")))
                .containsExactlyInAnyOrder("appid", "name", "hours", "ratio");
        assertThat(keys(body.get("stats").get("topTags").get(0))).containsExactlyInAnyOrder("tag", "hours", "ratio");
        assertThat(keys(body.get("ledger"))).containsExactlyInAnyOrder("code", "text", "params");
        assertThat(keys(body.get("verdict"))).containsExactlyInAnyOrder("crime", "sentence");
        assertThat(keys(body.get("verdict").get("crime"))).containsExactlyInAnyOrder("code", "text", "params");
        assertThat(body.get("gamerStats")).hasSize(5);
        assertThat(keys(body.get("gamerStats").get(0))).containsExactlyInAnyOrder("code", "value", "label");
        assertThat(keys(body.get("diagnosis")))
                .containsExactlyInAnyOrder("favoriteTag", "concentratedGames", "hiddenTaste");
        assertThat(keys(body.get("diagnosis").get("concentratedGames").get(0)))
                .containsExactlyInAnyOrder("appid", "name", "hours", "ratio");
    }

    @Test
    void analyze_defaultLangIsKo_enParamGivesEnglish() throws Exception {
        when(service.analyze(anyString()))
                .thenReturn(new LibraryAnalysisService.Analyzed("aid-1", profile, analysis));

        JsonNode ko = json(analyze("", BODY));
        JsonNode en = json(analyze("?lang=en", BODY));

        String code = analysis.ledger().code();
        String koExpected = loader.content().phrases().get(code).get(analysis.ledger().phraseIndex()).text().ko();
        String enExpected = loader.content().phrases().get(code).get(analysis.ledger().phraseIndex()).text().en();
        assertThat(ko.get("ledger").get("text").asText()).isEqualTo(koExpected);
        assertThat(en.get("ledger").get("text").asText()).isEqualTo(enExpected);
        assertThat(ko.get("gamerStats").get(0).get("label").asText())
                .isNotEqualTo(en.get("gamerStats").get(0).get("label").asText());
    }

    @Test
    void analyze_recordsCompleteEventOnlyWithClientId() throws Exception {
        when(service.analyze(anyString()))
                .thenReturn(new LibraryAnalysisService.Analyzed("aid-1", profile, analysis));

        analyze("", BODY);
        verify(eventService).record("cid-1", FunnelEventType.LIBRARY_COMPLETE, null, null);

        org.mockito.Mockito.clearInvocations(eventService);
        analyze("", "{\"steamId\":\"76561198012345678\"}");
        verify(eventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void analyze_renderFailureLeavesNoEvent() throws Exception {
        LibraryAnalysis broken = new LibraryAnalysis(false, analysis.stats(),
                new LibraryAnalysis.Judged("NO_SUCH_CODE", 0, Map.of()), analysis.verdict(),
                analysis.gamerStats(), analysis.diagnosis());
        when(service.analyze(anyString()))
                .thenReturn(new LibraryAnalysisService.Analyzed("aid-1", profile, broken));

        assertThat(analyze("", BODY).getResponse().getStatus()).isEqualTo(500);

        verify(eventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void analyze_noEventOnFailure() throws Exception {
        when(service.analyze(anyString())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "LIBRARY_PRIVATE"));

        analyze("", BODY);

        verify(eventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void analyze_errorStatusesAreMapped() throws Exception {
        Object[][] cases = {
                {HttpStatus.BAD_REQUEST, "INVALID_STEAM_ID"},
                {HttpStatus.NOT_FOUND, "STEAM_PROFILE_NOT_FOUND"},
                {HttpStatus.FORBIDDEN, "LIBRARY_PRIVATE"},
                {HttpStatus.UNPROCESSABLE_ENTITY, "LIBRARY_EMPTY"},
        };
        for (Object[] c : cases) {
            doThrow(new ResponseStatusException((HttpStatus) c[0], (String) c[1])).when(service).analyze(anyString());
            MvcResult res = analyze("", BODY);
            assertThat(res.getResponse().getStatus()).isEqualTo(((HttpStatus) c[0]).value());
            assertThat(json(res).get("message").asText()).isEqualTo(c[1]);
        }
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "STEAM_UNAVAILABLE"))
                .when(service).analyze(anyString());
        assertThat(analyze("", BODY).getResponse().getStatus()).isEqualTo(503);
    }

    @Test
    void analyze_validation_400() throws Exception {
        assertThat(analyze("", "{\"steamId\":\"\"}").getResponse().getStatus()).isEqualTo(400);
        assertThat(analyze("", "{}").getResponse().getStatus()).isEqualTo(400);
        assertThat(analyze("", "{\"steamId\":\"" + "a".repeat(201) + "\"}").getResponse().getStatus()).isEqualTo(400);
        assertThat(analyze("", "{\"steamId\":\"abc\",\"clientId\":\"bad id!\"}").getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    void share_ok() throws Exception {
        when(service.share("aid-1")).thenReturn("Xk3shareId");

        mvc.perform(post("/api/library/share").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisId\":\"aid-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareId").value("Xk3shareId"));
    }

    @Test
    void share_expired_404() throws Exception {
        when(service.share(anyString())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "ANALYSIS_EXPIRED"));

        mvc.perform(post("/api/library/share").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisId\":\"old\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ANALYSIS_EXPIRED"));
    }

    @Test
    void share_validation_400() throws Exception {
        mvc.perform(post("/api/library/share").contentType(MediaType.APPLICATION_JSON).content("{\"analysisId\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/library/share").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisId\":\"" + "a".repeat(65) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getShare_ok_noAnalysisIdNoProfile() throws Exception {
        when(service.renderShared(eq("S1"), any())).thenReturn(
                LibraryViewMapper.toSharedResponse(analysis, loader.content(), Lang.EN));

        MvcResult res = mvc.perform(get("/api/library/share/S1").param("lang", "en")).andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(res);
        assertThat(keys(body)).containsExactlyInAnyOrder("limitedData", "stats", "ledger", "verdict", "gamerStats",
                "diagnosis");
        String code = analysis.ledger().code();
        assertThat(body.get("ledger").get("text").asText())
                .isEqualTo(loader.content().phrases().get(code).get(analysis.ledger().phraseIndex()).text().en());
    }

    @Test
    void getShare_unknown_404() throws Exception {
        when(service.renderShared(anyString(), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "SHARE_NOT_FOUND"));

        mvc.perform(get("/api/library/share/none"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("SHARE_NOT_FOUND"));
    }

    // ---- 공유 읽기 언어

    @Test
    void getShare_passesLangToService() throws Exception {
        when(service.renderShared(anyString(), any())).thenReturn(
                LibraryViewMapper.toSharedResponse(analysis, loader.content(), Lang.KO));

        mvc.perform(get("/api/library/share/S1").param("lang", "en")).andExpect(status().isOk());
        verify(service).renderShared("S1", Lang.EN);

        mvc.perform(get("/api/library/share/S2")).andExpect(status().isOk());
        verify(service).renderShared("S2", Lang.KO);

        mvc.perform(get("/api/library/share/S3").param("lang", "xx")).andExpect(status().isOk());
        verify(service).renderShared("S3", Lang.from("xx"));
    }

    // ---- 값이 없는 항목도 키는 남는다

    private LibraryAnalysis zeroPlaytimeAnalysis() {
        List<OwnedGame> games = new ArrayList<>();
        for (long i = 1; i <= 7; i++) {
            games.add(new OwnedGame(i, "Idle" + i, 0, 0, null));
        }
        return LibraryAnalyzer.analyze(games, Map.of(), t -> 0.5, loader.content(), profile.steamId64());
    }

    private void assertNullKeysPresent(JsonNode body) {
        JsonNode stats = body.get("stats");
        assertThat(stats.has("mostPlayed")).isTrue();
        assertThat(stats.get("mostPlayed").isNull()).isTrue();

        Set<String> nullStats = new TreeSet<>();
        for (JsonNode stat : body.get("gamerStats")) {
            assertThat(keys(stat)).containsExactlyInAnyOrder("code", "value", "label");
            if (stat.get("value").isNull()) {
                nullStats.add(stat.get("code").asText());
            }
        }
        assertThat(nullStats).containsExactlyInAnyOrder("EXPLORATION", "STRATEGY", "DIVERSITY", "COOP");

        JsonNode diagnosis = body.get("diagnosis");
        assertThat(diagnosis.has("favoriteTag")).isTrue();
        assertThat(diagnosis.get("favoriteTag").isNull()).isTrue();
        assertThat(diagnosis.has("hiddenTaste")).isTrue();
        assertThat(diagnosis.get("hiddenTaste").isNull()).isTrue();
        assertThat(diagnosis.get("concentratedGames").isArray()).isTrue();
    }

    @Test
    void analyze_zeroPlaytime_keepsNullValuedKeys() throws Exception {
        when(service.analyze(anyString()))
                .thenReturn(new LibraryAnalysisService.Analyzed("aid-0", profile, zeroPlaytimeAnalysis()));

        MvcResult res = analyze("", BODY);

        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertNullKeysPresent(json(res));
    }

    @Test
    void getShare_zeroPlaytime_keepsNullValuedKeys() throws Exception {
        when(service.renderShared(anyString(), any())).thenReturn(
                LibraryViewMapper.toSharedResponse(zeroPlaytimeAnalysis(), loader.content(), Lang.KO));

        MvcResult res = mvc.perform(get("/api/library/share/Z1")).andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertNullKeysPresent(json(res));
    }
}
