package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.ReviewDetail;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AppReviewsParserTest {

    final AppReviewsParser parser = new AppReviewsParser(new ObjectMapper());

    // IUserReviewsService/GetAppReviews 응답 모양, 본문은 response 로 감싸져 있고 success 필드가 없다
    @Test
    void parsesSummary_andFirstPositiveAndNegativeSamples() {
        String json = """
            {"response":{"query_summary":{"num_reviews":3,
                "review_score_desc":"Very Positive","total_positive":80,"total_negative":20,"total_reviews":100},
             "reviews":[
                {"review":"skip - has no vote match yet","voted_up":true,
                 "author":{"steamid":"76561190000000000","playtime_at_review":120}},
                {"review":"terrible","voted_up":false},
                {"review":"later positive ignored","voted_up":true}],
             "cursor":"AoJ"}}
            """;

        ReviewDetail d = parser.parse(570L, json, 2000).orElseThrow();

        assertThat(d.reviewScoreDesc()).isEqualTo("Very Positive");
        assertThat(d.totalReviews()).isEqualTo(100);
        assertThat(d.positivePercentage()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(d.positiveText()).isEqualTo("skip - has no vote match yet");
        assertThat(d.negativeText()).isEqualTo("terrible");
    }

    // 2026-11-23 실제 GetAppReviews 응답(appid 570)을 줄인 자료, 리뷰어 steamid 는 지웠다
    // 손으로 만든 자료만으로는 필드 이름이나 감싸는 모양이 실제와 다른 것을 못 잡는다
    @Test
    void parsesRealResponseFixture() throws Exception {
        String json;
        try (var in = getClass().getResourceAsStream("/steam/get-app-reviews-570.json")) {
            json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        ReviewDetail d = parser.parse(570L, json, 2000).orElseThrow();

        assertThat(d.totalReviews()).isPositive();
        assertThat(d.reviewScoreDesc()).isNotBlank();
        assertThat(d.negativeText()).startsWith("This game lacks balance.");
        assertThat(d.positiveText()).startsWith("Played for 48 minutes.");
    }

    @Test
    void truncatesSampleTexts_toMaxChars() {
        String big = "x".repeat(50);
        String json = "{\"response\":{\"query_summary\":{\"total_reviews\":1,\"total_positive\":1},"
                + "\"reviews\":[{\"review\":\"" + big + "\",\"voted_up\":true}]}}";

        ReviewDetail d = parser.parse(1L, json, 10).orElseThrow();

        assertThat(d.positiveText()).hasSize(10);
        assertThat(d.negativeText()).isNull();
    }

    // 리뷰가 없는 게임도 정상 응답이다, 샘플 칸이 비어 있을 뿐 응답 이상이 아니다
    @Test
    void gameWithoutReviews_isParsedWithNullSamples() {
        String json = "{\"response\":{\"query_summary\":{\"num_reviews\":0,\"total_reviews\":0,\"total_positive\":0},\"reviews\":[]}}";
        ReviewDetail d = parser.parse(1L, json, 2000).orElseThrow();
        assertThat(d.positiveText()).isNull();
        assertThat(d.negativeText()).isNull();
        assertThat(d.positivePercentage()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void reviewsArrayMissing_isStillParsedWhenSummaryPresent() {
        String json = "{\"response\":{\"query_summary\":{\"num_reviews\":0}}}";
        ReviewDetail d = parser.parse(1L, json, 2000).orElseThrow();
        assertThat(d.positiveText()).isNull();
        assertThat(d.negativeText()).isNull();
    }

    // 비어 있음은 응답 이상이다, 2026-10 폐기된 상점 주소가 상점 첫 화면 HTML 로 넘어가 조용히 "리뷰 없음"으로 처리된 적이 있다
    @Test
    void htmlBlankOrUnexpectedShape_yieldsEmpty() {
        assertThat(parser.parse(1L, "<!DOCTYPE html><html><head></head></html>", 2000)).isEmpty();
        assertThat(parser.parse(1L, "", 2000)).isEmpty();
        assertThat(parser.parse(1L, null, 2000)).isEmpty();
        assertThat(parser.parse(1L, "{}", 2000)).isEmpty();
        assertThat(parser.parse(1L, "{\"response\":{}}", 2000)).isEmpty();
        assertThat(parser.parse(1L, "{\"success\":1,\"query_summary\":{},\"reviews\":[]}", 2000)).isEmpty();
    }
}
