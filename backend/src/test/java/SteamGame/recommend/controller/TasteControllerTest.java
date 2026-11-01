package SteamGame.recommend.controller;

import SteamGame.recommend.config.RateLimitProperties;
import SteamGame.recommend.config.SecurityConfig;
import SteamGame.recommend.domain.event.entity.FunnelEventType;
import SteamGame.recommend.domain.event.service.FunnelEventService;
import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.feedback.service.FeedbackSuppressionService;
import SteamGame.recommend.domain.impression.service.ImpressionService;
import SteamGame.recommend.domain.recommendation.dto.ExplanationItem;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.taste.content.Lang;
import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import SteamGame.recommend.domain.taste.content.TasteType;
import SteamGame.recommend.domain.taste.service.GameExplanation;
import SteamGame.recommend.domain.taste.service.TasteRecommendResult;
import SteamGame.recommend.domain.taste.service.TasteRecommendService;
import SteamGame.recommend.infra.web.ClientIpResolver;
import SteamGame.recommend.infra.web.RateLimitFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TasteController.class)
@Import({SecurityConfig.class, RateLimitFilter.class, RateLimitProperties.class, ClientIpResolver.class,
        TasteContentLoader.class})
class TasteControllerTest {

    @Autowired MockMvc mvc;
    @Autowired TasteContentLoader loader;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean TasteRecommendService recommendService;
    @MockitoBean FeedbackSuppressionService suppressionService;
    @MockitoBean ImpressionService impressionService;
    @MockitoBean FunnelEventService eventService;

    private static String answersJson(String value, int count) {
        return "[" + java.util.Collections.nCopies(count, "\"" + value + "\"").stream()
                .collect(Collectors.joining(",")) + "]";
    }

    private static SteamAppDTO game(long appid) {
        SteamAppDTO dto = new SteamAppDTO();
        dto.setAppid(appid);
        dto.setName("game" + appid);
        return dto;
    }

    private String koText(int index) {
        return loader.content().questions().get(index).text().get(Lang.KO);
    }

    // ---------- questions ----------

    @Test
    void questions_ko() throws Exception {
        MvcResult res = mvc.perform(get("/api/taste/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.questions.length()").value(12))
                .andExpect(jsonPath("$.questions[0].text").value(koText(0)))
                .andReturn();

        // axis, side가 null 값으로라도 실리면 안 되므로 키 집합을 직접 본다
        JsonNode question = objectMapper.readTree(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .get("questions").get(0);
        Set<String> keys = new TreeSet<>();
        question.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactly("id", "options", "text");
        Set<String> optionKeys = new TreeSet<>();
        question.get("options").get(0).fieldNames().forEachRemaining(optionKeys::add);
        assertThat(optionKeys).containsExactly("key", "text");
    }

    @Test
    void questions_langEN_uppercase() throws Exception {
        String en = loader.content().questions().get(0).text().get(Lang.EN);
        mvc.perform(get("/api/taste/questions?lang=EN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].text").value(en));
    }

    @Test
    void questions_langUnknown_fallsBackToKo() throws Exception {
        mvc.perform(get("/api/taste/questions?lang=ja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].text").value(koText(0)));
    }

    @Test
    void questions_cacheHeader() throws Exception {
        mvc.perform(get("/api/taste/questions"))
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
                .andExpect(header().string("Cache-Control", containsString("public")));
    }

    // ---------- types ----------

    @Test
    void types_list16() throws Exception {
        mvc.perform(get("/api/taste/types"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
                .andExpect(jsonPath("$.length()").value(16))
                .andExpect(jsonPath("$[0].code").isNotEmpty())
                .andExpect(jsonPath("$[0].name").isNotEmpty())
                .andExpect(jsonPath("$[0].work").isNotEmpty())
                .andExpect(jsonPath("$[0].oneLiner").isNotEmpty());
    }

    @Test
    void type_lowercasePathNormalized() throws Exception {
        when(recommendService.featuredGames(any(TasteType.class))).thenReturn(List.of(game(11L), game(22L)));

        mvc.perform(get("/api/taste/types/gmhs"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
                .andExpect(jsonPath("$.code").value("GMHS"))
                .andExpect(jsonPath("$.axes.length()").value(4))
                .andExpect(jsonPath("$.axes[0].id").value("EG"))
                .andExpect(jsonPath("$.axes[0].letter").value("G"))
                .andExpect(jsonPath("$.featuredGames.length()").value(2))
                .andExpect(jsonPath("$.featuredGames[0].appid").value(11))
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.share.title").isNotEmpty())
                .andExpect(jsonPath("$.workAppid").isNumber());
    }

    @Test
    void type_unknown404() throws Exception {
        mvc.perform(get("/api/taste/types/XXXX"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("TASTE_TYPE_NOT_FOUND"));
    }

    // ---------- result ----------

    private void stubFeaturedForEnhs() {
        TasteType enhs = loader.content().type("ENHS").orElseThrow();
        when(recommendService.featuredGames(any(TasteType.class)))
                .thenReturn(enhs.featuredAppids().stream().map(TasteControllerTest::game).toList());
    }

    @Test
    void result_ok() throws Exception {
        stubFeaturedForEnhs();
        List<Long> featured = loader.content().type("ENHS").orElseThrow().featuredAppids();

        mvc.perform(post("/api/taste/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v1\",\"answers\":" + answersJson("A", 12) + ",\"clientId\":\"c1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ENHS"))
                .andExpect(jsonPath("$.axisScores.EG.E").value(3))
                .andExpect(jsonPath("$.type.code").value("ENHS"))
                .andExpect(jsonPath("$.type.featuredGames.length()").value(featured.size()));

        verify(eventService).record("c1", FunnelEventType.TEST_COMPLETE, "ENHS", null);
        verify(impressionService).record("c1", null, FeedbackContext.TASTE, featured);
    }

    @Test
    void result_lowercaseAnswer400() throws Exception {
        String answers = "[\"a\"," + answersJson("A", 11).substring(1);
        mvc.perform(post("/api/taste/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v1\",\"answers\":" + answers + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_ANSWERS"));

        verify(eventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void result_length11_400() throws Exception {
        mvc.perform(post("/api/taste/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v1\",\"answers\":" + answersJson("A", 11) + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_ANSWERS"));

        verify(eventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void result_nullElement400() throws Exception {
        String answers = "[null," + answersJson("A", 11).substring(1);
        mvc.perform(post("/api/taste/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v1\",\"answers\":" + answers + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_ANSWERS"));

        verify(eventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void result_versionMismatch409() throws Exception {
        mvc.perform(post("/api/taste/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v0\",\"answers\":" + answersJson("A", 12) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("TASTE_VERSION_MISMATCH"));

        verify(eventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void result_clientIdOver64_400() throws Exception {
        mvc.perform(post("/api/taste/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v1\",\"answers\":" + answersJson("A", 12)
                                + ",\"clientId\":\"" + "x".repeat(65) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void result_missingAnswers400() throws Exception {
        mvc.perform(post("/api/taste/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_ANSWERS"));

        verify(eventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void result_langEn_typeNameInEnglish() throws Exception {
        stubFeaturedForEnhs();
        String en = loader.content().type("ENHS").orElseThrow().name().get(Lang.EN);

        mvc.perform(post("/api/taste/result?lang=en").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v1\",\"answers\":" + answersJson("A", 12) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type.name").value(en));
    }

    @Test
    void recommend_unknownCode404_beforeSuppression() throws Exception {
        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"XXXX\",\"clientId\":\"c1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("TASTE_TYPE_NOT_FOUND"));

        verify(suppressionService, never()).resolveExcludes(any(), any());
        verify(recommendService, never()).recommend(any(), anyInt(), any(), any(), any());
    }

    // ---------- recommend ----------

    private TasteRecommendResult twoGames() {
        return new TasteRecommendResult(List.of(game(1L), game(2L)), List.of(
                new GameExplanation(1L, List.of(new ExplanationItem("TASTE_TAG_MATCH", Map.of("tags", List.of("Strategy"))))),
                new GameExplanation(2L, List.of(new ExplanationItem("TASTE_TAG_MATCH", Map.of("tags", List.of("Strategy")))))));
    }

    private static String recommendBody(String extra) {
        return "{\"code\":\"GMHS\",\"clientId\":\"c1\"" + extra + "}";
    }

    @Test
    void recommend_ok() throws Exception {
        when(suppressionService.resolveExcludes(any(), any())).thenReturn(Set.of());
        when(recommendService.recommend(eq("GMHS"), anyInt(), any(), any(), any())).thenReturn(twoGames());

        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON).content(recommendBody("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games.length()").value(2))
                .andExpect(jsonPath("$.explanations[0].appid").value(1))
                .andExpect(jsonPath("$.explanations[0].items[0].code").value("TASTE_TAG_MATCH"));

        verify(impressionService).record("c1", null, FeedbackContext.TASTE, List.of(1L, 2L));
    }

    @Test
    void recommend_defaultReview300() throws Exception {
        when(suppressionService.resolveExcludes(any(), any())).thenReturn(Set.of());
        when(recommendService.recommend(any(), anyInt(), any(), any(), any())).thenReturn(twoGames());

        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON).content(recommendBody("")))
                .andExpect(status().isOk());

        verify(recommendService).recommend(eq("GMHS"), eq(300), isNull(), isNull(), any());
    }

    @Test
    void recommend_passesReviewKoreanFree() throws Exception {
        when(suppressionService.resolveExcludes(any(), any())).thenReturn(Set.of());
        when(recommendService.recommend(any(), anyInt(), any(), any(), any())).thenReturn(twoGames());

        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content(recommendBody(",\"review\":50,\"korean\":true,\"free\":false")))
                .andExpect(status().isOk());

        verify(recommendService).recommend(eq("GMHS"), eq(50), eq(true), eq(false), any());
    }

    @Test
    void recommend_suppressionRetry() throws Exception {
        Set<Long> original = Set.of(5L);
        Set<Long> widened = Set.of(5L, 6L, 7L);
        when(suppressionService.resolveExcludes(any(), any())).thenReturn(widened);
        when(recommendService.recommend(eq("GMHS"), anyInt(), any(), any(), eq(widened)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "none"));
        when(recommendService.recommend(eq("GMHS"), anyInt(), any(), any(), eq(original))).thenReturn(twoGames());

        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content(recommendBody(",\"excludeAppids\":[5]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games.length()").value(2));

        verify(recommendService, times(2)).recommend(any(), anyInt(), any(), any(), any());
    }

    @Test
    void recommend_404WithoutSuppression_notRetried() throws Exception {
        when(suppressionService.resolveExcludes(any(), any())).thenReturn(Set.of(5L));
        when(recommendService.recommend(any(), anyInt(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "none"));

        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content(recommendBody(",\"excludeAppids\":[5]")))
                .andExpect(status().isNotFound());

        verify(recommendService, times(1)).recommend(any(), anyInt(), any(), any(), any());
        verify(impressionService, never()).record(any(), any(), any(), any());
    }

    private static String excludeJson(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(Long::toString).collect(Collectors.joining(",", "[", "]"));
    }

    @Test
    void recommend_exclude500_ok() throws Exception {
        when(suppressionService.resolveExcludes(any(), any())).thenReturn(Set.of());
        when(recommendService.recommend(any(), anyInt(), any(), any(), any())).thenReturn(twoGames());

        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content(recommendBody(",\"excludeAppids\":" + excludeJson(500))))
                .andExpect(status().isOk());
    }

    @Test
    void recommend_exclude1000_400() throws Exception {
        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content(recommendBody(",\"excludeAppids\":" + excludeJson(1000))))
                .andExpect(status().isBadRequest());

        verify(recommendService, never()).recommend(any(), anyInt(), any(), any(), any());
    }

    @Test
    void recommend_blankCode400() throws Exception {
        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recommend_negativeReview400() throws Exception {
        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content(recommendBody(",\"review\":-1")))
                .andExpect(status().isBadRequest());
    }
}
