package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.ReviewDetail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Slf4j
@Component
public class AppReviewsParser {

    private final ObjectMapper objectMapper;

    public AppReviewsParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<ReviewDetail> parse(long appid, String json, int maxChars) {
        if (json == null || json.isBlank()) return Optional.empty();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.path("success").asInt(0) != 1) return Optional.empty();

            JsonNode qs = root.path("query_summary");
            String scoreDesc = trunc(text(qs, "review_score_desc"), 50);
            int totalReviews = qs.path("total_reviews").asInt(0);
            int totalPositive = qs.path("total_positive").asInt(0);
            BigDecimal pct = totalReviews <= 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(totalPositive).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalReviews), 2, RoundingMode.HALF_UP);

            String positiveText = null, negativeText = null;
            for (JsonNode r : root.path("reviews")) {
                String t = r.path("review").asText("").strip();
                if (t.isBlank()) continue;
                boolean up = r.path("voted_up").asBoolean(false);
                if (up && positiveText == null) positiveText = trunc(t, maxChars);
                else if (!up && negativeText == null) negativeText = trunc(t, maxChars);
                if (positiveText != null && negativeText != null) break;
            }
            return Optional.of(new ReviewDetail(appid, scoreDesc, totalReviews, pct, positiveText, negativeText));
        } catch (Exception ex) {
            log.error("Failed to parse appreviews for appid {}", appid, ex);
            return Optional.empty();
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static String trunc(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
