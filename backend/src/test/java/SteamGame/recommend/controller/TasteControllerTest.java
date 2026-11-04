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
import SteamGame.recommend.domain.taste.content.TasteAxis;
import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import SteamGame.recommend.domain.taste.content.TasteType;
import SteamGame.recommend.domain.taste.content.TasteV2ContentLoader;
import SteamGame.recommend.domain.taste.service.GameExplanation;
import SteamGame.recommend.domain.taste.service.TasteRecommendResult;
import SteamGame.recommend.domain.taste.service.TasteRecommendService;
import SteamGame.recommend.infra.web.ClientIpResolver;
import SteamGame.recommend.infra.web.RateLimitFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        TasteContentLoader.class, TasteV2ContentLoader.class})
class TasteControllerTest {

    @Autowired MockMvc mvc;
    @Autowired TasteContentLoader loader;
    @Autowired TasteV2ContentLoader v2Loader;
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

    // ---------- v2 questions ----------

    @Test
    void questions_explicitV1_sameAsDefault() throws Exception {
        mvc.perform(get("/api/taste/questions?version=v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.questions.length()").value(12));
    }

    @Test
    void questions_v2() throws Exception {
        String ko = v2Loader.content().questions().get(0).text().get(Lang.KO);
        MvcResult res = mvc.perform(get("/api/taste/questions?version=v2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
                .andExpect(jsonPath("$.version").value("v2"))
                .andExpect(jsonPath("$.questions.length()").value(16))
                .andExpect(jsonPath("$.questions[0].text").value(ko))
                .andExpect(jsonPath("$.questions[0].options.length()").value(4))
                .andReturn();
        // 축과 점수가 응답에 실리면 안 되므로 키 집합을 직접 본다
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
    void questions_unknownVersion_409() throws Exception {
        mvc.perform(get("/api/taste/questions?version=v3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("TASTE_VERSION_MISMATCH"));
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

    // ---------- v2 result ----------

    private static String v2Body(String answersJson) {
        return "{\"version\":\"v2\",\"answers\":" + answersJson + ",\"clientId\":\"client-1\"}";
    }

    // 실제 파일은 보기마다 점수 배치가 섞여 있다, 문항마다 왼쪽 3점 보기를 골라 모든 축이 왼쪽 12점이 되게 한다
    private String allLeftStrongestJson() {
        List<TasteAxis> axes = loader.content().axes();
        List<String> keys = v2Loader.content().questions().stream()
                .map(q -> {
                    String left = axes.stream().filter(a -> a.id().equals(q.axis())).findFirst().orElseThrow()
                            .left().code();
                    return q.options().stream().filter(o -> o.scores().get(left) == 3).findFirst().orElseThrow().key();
                })
                .toList();
        return "[" + keys.stream().map(k -> "\"" + k + "\"").collect(Collectors.joining(",")) + "]";
    }

    @Test
    void result_v2_allLeftStrongest() throws Exception {
        when(recommendService.featuredGames(any())).thenReturn(List.of(game(10L)));
        String summaryKo = v2Loader.content().axisSummaries().get("EG").get("E_STRONG").get(Lang.KO);
        mvc.perform(post("/api/taste/result").contentType(MediaType.APPLICATION_JSON)
                        .content(v2Body(allLeftStrongestJson())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v2"))
                .andExpect(jsonPath("$.code").value("ENHS"))
                .andExpect(jsonPath("$.axisScores.EG.E").value(12))
                .andExpect(jsonPath("$.axisDetails.length()").value(4))
                .andExpect(jsonPath("$.axisDetails[0].id").value("EG"))
                .andExpect(jsonPath("$.axisDetails[0].total").value(12))
                .andExpect(jsonPath("$.axisDetails[0].leftPercent").value(100))
                .andExpect(jsonPath("$.axisDetails[0].winner").value("E"))
                .andExpect(jsonPath("$.axisDetails[0].balance").value("STRONG"))
                .andExpect(jsonPath("$.axisDetails[0].tieBroken").value(false))
                .andExpect(jsonPath("$.axisDetails[0].summary").value(summaryKo))
                .andExpect(jsonPath("$.type.code").value("ENHS"));
        verify(eventService).record("client-1", FunnelEventType.TEST_COMPLETE, "ENHS", null);
        verify(impressionService).record("client-1", null, FeedbackContext.TASTE, List.of(10L));
    }

    // 실제 문항에서 전부 A를 고르면 네 축 모두 6:6이고 해석 A 동점 규칙으로 EMCS가 된다
    // EG는 1번 A (3,0), NM은 6번 A (0,3), HC는 3번 A (0,3), SP는 8번 A (3,0)이 기준 답이다
    // 문항 점수 배치가 바뀌면 이 기대값도 바뀐다, 점수가 바뀌면 새 콘텐츠 버전을 만드는 것이 설계 규칙이다
    @Test
    void result_v2_allA_tiesEveryAxis() throws Exception {
        when(recommendService.featuredGames(any())).thenReturn(List.of());
        String evenKo = v2Loader.content().axisSummaries().get("EG").get("EVEN").get(Lang.KO);
        mvc.perform(post("/api/taste/result").contentType(MediaType.APPLICATION_JSON)
                        .content(v2Body(answersJson("A", 16))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EMCS"))
                .andExpect(jsonPath("$.axisScores.EG.E").value(6))
                .andExpect(jsonPath("$.axisDetails[*].leftPercent").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(50))))
                .andExpect(jsonPath("$.axisDetails[*].tieBroken").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))))
                .andExpect(jsonPath("$.axisDetails[*].balance").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("BALANCED"))))
                .andExpect(jsonPath("$.axisDetails[0].summary").value(evenKo))
                .andExpect(jsonPath("$.type.code").value("EMCS"));
    }

    // 같은 답이면 언어와 상관없이 code와 점수가 같고 summary만 언어를 따른다
    @Test
    void result_v2_langEnChangesOnlySummary() throws Exception {
        when(recommendService.featuredGames(any())).thenReturn(List.of());
        String summaryEn = v2Loader.content().axisSummaries().get("EG").get("E_STRONG").get(Lang.EN);
        mvc.perform(post("/api/taste/result?lang=en").contentType(MediaType.APPLICATION_JSON)
                        .content(v2Body(allLeftStrongestJson())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ENHS"))
                .andExpect(jsonPath("$.axisScores.EG.E").value(12))
                .andExpect(jsonPath("$.axisDetails[0].summary").value(summaryEn));
    }

    @Test
    void result_v2_badAnswers_400() throws Exception {
        // 17개는 앞의 16개만 읽고 나머지를 버리는 구현을 막는다
        for (String answers : List.of(answersJson("A", 12), answersJson("A", 17), answersJson("a", 16),
                answersJson("E", 16), "[" + "\"A\",".repeat(15) + "null]")) {
            mvc.perform(post("/api/taste/result").contentType(MediaType.APPLICATION_JSON)
                            .content(v2Body(answers)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void result_unknownVersion_409() throws Exception {
        mvc.perform(post("/api/taste/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v3\",\"answers\":" + answersJson("A", 16) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("TASTE_VERSION_MISMATCH"));
    }

    // v1 응답에는 새 필드가 null로도 끼면 안 된다
    @Test
    void result_v1_hasNoV2Fields() throws Exception {
        when(recommendService.featuredGames(any())).thenReturn(List.of());
        MvcResult res = mvc.perform(post("/api/taste/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v1\",\"answers\":" + answersJson("A", 12) + "}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        Set<String> keys = new TreeSet<>();
        body.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactly("axisScores", "code", "type");
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

    @Test
    void recommend_suppressionRetryWithoutExcludeAppids_usesEmptySetNotNull() throws Exception {
        Set<Long> widened = Set.of(6L, 7L);
        when(suppressionService.resolveExcludes(any(), any())).thenReturn(widened);
        when(recommendService.recommend(eq("GMHS"), anyInt(), any(), any(), eq(widened)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "none"));
        when(recommendService.recommend(eq("GMHS"), anyInt(), any(), any(), eq(Set.<Long>of()))).thenReturn(twoGames());

        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON).content(recommendBody("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games.length()").value(2));

        ArgumentCaptor<Set<Long>> retryArg = ArgumentCaptor.forClass(Set.class);
        verify(recommendService, times(2)).recommend(any(), anyInt(), any(), any(), retryArg.capture());
        assertThat(retryArg.getAllValues().get(1)).isNotNull().isEmpty();
    }

    @Test
    void recommend_nullElementInExclude_ignored() throws Exception {
        when(suppressionService.resolveExcludes(any(), any())).thenReturn(Set.of());
        when(recommendService.recommend(any(), anyInt(), any(), any(), any())).thenReturn(twoGames());

        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content(recommendBody(",\"excludeAppids\":[5,null]")))
                .andExpect(status().isOk());
    }

    @Test
    void recommend_clientIdWithNewline_400() throws Exception {
        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"GMHS\",\"clientId\":\"bad\nid\"}"))
                .andExpect(status().isBadRequest());

        verify(recommendService, never()).recommend(any(), anyInt(), any(), any(), any());
    }

    @Test
    void result_clientIdWithNewline_400() throws Exception {
        mvc.perform(post("/api/taste/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v1\",\"answers\":" + answersJson("A", 12)
                                + ",\"clientId\":\"bad\nid\"}"))
                .andExpect(status().isBadRequest());

        verify(eventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void clientIdAllowedCharsAndNull_ok() throws Exception {
        when(suppressionService.resolveExcludes(any(), any())).thenReturn(Set.of());
        when(recommendService.recommend(any(), anyInt(), any(), any(), any())).thenReturn(twoGames());

        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"GMHS\",\"clientId\":\"Ab_9-z\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/taste/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"GMHS\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void type_tagsKeepTagAndWeightFieldNames() throws Exception {
        when(recommendService.featuredGames(any(TasteType.class))).thenReturn(List.of());

        MvcResult res = mvc.perform(get("/api/taste/types/GMHS")).andExpect(status().isOk()).andReturn();

        JsonNode tag = objectMapper.readTree(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .get("tags").get(0);
        Set<String> keys = new TreeSet<>();
        tag.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactly("tag", "weight");
        assertThat(tag.get("weight").isNumber()).isTrue();
    }
}
