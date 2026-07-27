package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.ReviewDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AppReviewsParserTest {

    final AppReviewsParser parser = new AppReviewsParser(new ObjectMapper());

    @Test
    void parsesSummary_andFirstPositiveAndNegativeSamples() {
        String json = """
            {"success":1,"query_summary":{
                "review_score_desc":"Very Positive","total_positive":80,"total_negative":20,"total_reviews":100},
             "reviews":[
                {"review":"skip - has no vote match yet","voted_up":true},
                {"review":"terrible","voted_up":false},
                {"review":"later positive ignored","voted_up":true}]}
            """;

        ReviewDetail d = parser.parse(570L, json, 2000).orElseThrow();

        assertThat(d.reviewScoreDesc()).isEqualTo("Very Positive");
        assertThat(d.totalReviews()).isEqualTo(100);
        assertThat(d.positivePercentage()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(d.positiveText()).isEqualTo("skip - has no vote match yet");
        assertThat(d.negativeText()).isEqualTo("terrible");
    }

    @Test
    void truncatesSampleTexts_toMaxChars() {
        String big = "x".repeat(50);
        String json = "{\"success\":1,\"query_summary\":{\"total_reviews\":1,\"total_positive\":1},"
                + "\"reviews\":[{\"review\":\"" + big + "\",\"voted_up\":true}]}";

        ReviewDetail d = parser.parse(1L, json, 10).orElseThrow();

        assertThat(d.positiveText()).hasSize(10);
        assertThat(d.negativeText()).isNull();
    }

    @Test
    void blankReviewTextsSkipped_andEmptyReviewsGiveNullSamples() {
        String json = "{\"success\":1,\"query_summary\":{\"total_reviews\":0,\"total_positive\":0},\"reviews\":[]}";
        ReviewDetail d = parser.parse(1L, json, 2000).orElseThrow();
        assertThat(d.positiveText()).isNull();
        assertThat(d.negativeText()).isNull();
        assertThat(d.positivePercentage()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void successZeroOrBlank_yieldsEmpty() {
        assertThat(parser.parse(1L, "{\"success\":0}", 2000)).isEmpty();
        assertThat(parser.parse(1L, "", 2000)).isEmpty();
        assertThat(parser.parse(1L, "garbage", 2000)).isEmpty();
    }
}
