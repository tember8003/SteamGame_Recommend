package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.ReviewDetail;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

    // IUserReviewsService/GetAppReviews 응답을 읽는다, 본문은 response 로 감싸져 있음
    // 빈 결과는 "응답 이상"이다, 리뷰가 없는 게임은 샘플 칸이 빈 결과로 돌려줘서 둘을 호출부가 구분해 센다
    // 폐기된 상점 주소가 HTML 이나 빈 본문을 줄 때 "리뷰 없음"으로 삼켜 한 달간 드러나지 않은 적이 있음
    public Optional<ReviewDetail> parse(long appid, String json, int maxChars) {
        if (json == null || json.isBlank()) {
            log.warn("appreviews 응답이 비어 있음, appid {}", appid);
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(json).path("response");
            JsonNode qs = root.path("query_summary");
            if (!qs.isObject()) {
                log.warn("appreviews 응답에 response.query_summary 가 없음, appid {}", appid);
                return Optional.empty();
            }

            String scoreDesc = trunc(text(qs, "review_score_desc"), 50);
            int totalReviews = qs.path("total_reviews").asInt(0);
            int totalPositive = qs.path("total_positive").asInt(0);
            BigDecimal pct = totalReviews <= 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(totalPositive).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalReviews), 2, RoundingMode.HALF_UP);

            String positiveText = null, negativeText = null;
            for (JsonNode r : root.path("reviews")) {
                String t = r.path("review").asString("").strip();
                if (t.isBlank()) continue;
                boolean up = r.path("voted_up").asBoolean(false);
                if (up && positiveText == null) positiveText = trunc(t, maxChars);
                else if (!up && negativeText == null) negativeText = trunc(t, maxChars);
                if (positiveText != null && negativeText != null) break;
            }
            return Optional.of(new ReviewDetail(appid, scoreDesc, totalReviews, pct, positiveText, negativeText));
        } catch (Exception ex) {
            // HTML 같은 응답은 게임마다 같은 스택이 반복되므로 메시지 한 줄만 남김
            log.warn("appreviews 응답을 읽지 못함, appid {}: {}", appid, ex.getMessage());
            return Optional.empty();
        }
    }

    // Jackson 3 의 asString() 은 객체나 배열에서 예외를 던지므로 기본값을 주는 형태로 null 을 받음
    private static String text(JsonNode n, String field) {
        return n.path(field).asString(null);
    }

    private static String trunc(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
