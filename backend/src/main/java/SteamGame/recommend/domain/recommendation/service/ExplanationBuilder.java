package SteamGame.recommend.domain.recommendation.service;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.entity.Review;
import SteamGame.recommend.domain.recommendation.MatchConstants;
import SteamGame.recommend.domain.recommendation.dto.ExplanationItem;
import SteamGame.recommend.domain.recommendation.dto.MatchExplanation;
import SteamGame.recommend.domain.recommendation.dto.RecommendationCriteria;
import SteamGame.recommend.domain.recommendation.dto.SessionLength;
import SteamGame.recommend.domain.recommendation.util.VolumeBuckets;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 후보 게임마다 사유와 주의점을 결정적 규칙으로 조립함, 문장을 굽지 않고 코드와 파라미터만 냄
// 상태가 없으나 다른 서비스와 주입 방식을 맞추려고 인스턴스 메서드로 둠
@Service
public class ExplanationBuilder {

    public MatchExplanation build(Game game, Review review, List<String> gameTags, RecommendationCriteria c,
                                   boolean moodPath, int rank, boolean reviewRelaxed, String likedGameName) {
        List<String> tags = gameTags == null ? List.of() : gameTags;
        boolean isFree = game != null && game.isFree();
        Integer priceCents = game == null ? null : game.getPrice();
        Integer reviewCount = game == null ? null : game.getReviewCount();
        BigDecimal positivePct = review == null ? null : review.getPositivePercentage();
        BigDecimal positiveHour = review == null ? null : review.getPositiveGameHour();
        Integer budgetCents = c.getBudgetCents();
        Integer players = c.getPlayers();
        SessionLength requestedSession = c.getSessionLength();
        SessionLength actualSession = VolumeBuckets.classify(positiveHour);

        List<ExplanationItem> reasons = new ArrayList<>();
        addBecauseYouLikedReason(reasons, likedGameName);
        addPriceReason(reasons, isFree, priceCents, budgetCents);
        addCoopReason(reasons, c.needsCoop(), tags);
        addSoloReason(reasons, players, tags);
        reasons.add(wellReviewedReason(positivePct, reviewCount));
        addVolumeFitsReason(reasons, requestedSession, actualSession);
        addMoodMatchReason(reasons, moodPath, rank);

        List<ExplanationItem> caveats = new ArrayList<>();
        addIf(caveats, c.needsCoop(), "PLAYER_COUNT_APPROX");
        addIf(caveats, budgetCents != null && !isFree, "PRICE_NO_DISCOUNT");
        addVolumeMismatchCaveat(caveats, requestedSession, actualSession, positiveHour);
        addIf(caveats, requestedSession != null && positiveHour == null, "VOLUME_UNKNOWN");
        addIf(caveats, c.hasMood() && !moodPath, "MOOD_UNAVAILABLE");
        addIf(caveats, reviewRelaxed, "REVIEW_RELAXED");

        return new MatchExplanation(reasons, caveats);
    }

    // 취향에 가장 가까운 좋아요 게임이 정해졌을 때만 맨 앞에 지명 사유를 냄, 임계 판정과 이름 조회는 오케스트레이션 책임임
    private void addBecauseYouLikedReason(List<ExplanationItem> reasons, String likedGameName) {
        if (likedGameName != null && !likedGameName.isBlank()) {
            reasons.add(new ExplanationItem("BECAUSE_YOU_LIKED", Map.of("gameName", likedGameName)));
        }
    }

    // 무료면 IS_FREE 만, 아니면 예산 이내일 때만 WITHIN_BUDGET 을 냄
    private void addPriceReason(List<ExplanationItem> reasons, boolean isFree, Integer priceCents, Integer budgetCents) {
        if (isFree) {
            reasons.add(new ExplanationItem("IS_FREE", Map.of()));
            return;
        }
        if (budgetCents != null && priceCents != null && priceCents <= budgetCents) {
            reasons.add(new ExplanationItem("WITHIN_BUDGET", Map.of("priceCents", priceCents, "budgetCents", budgetCents)));
        }
    }

    // 코옵이 필요할 때 게임 태그에 실제로 있는 코옵 계열 태그 중 첫 매칭만 param 으로 냄
    private void addCoopReason(List<ExplanationItem> reasons, boolean needsCoop, List<String> gameTags) {
        if (!needsCoop) {
            return;
        }
        gameTags.stream().filter(MatchConstants.COOP_TAGS::contains).findFirst()
                .ifPresent(tag -> reasons.add(new ExplanationItem("COOP_SUPPORTED", Map.of("tag", tag))));
    }

    // 혼자 플레이를 요청했고 싱글플레이 태그가 있을 때만 냄
    private void addSoloReason(List<ExplanationItem> reasons, Integer players, List<String> gameTags) {
        if (players != null && players == 1 && gameTags.contains("Singleplayer")) {
            reasons.add(new ExplanationItem("SOLO_FRIENDLY", Map.of()));
        }
    }

    // 품질 근거라 항상 냄, 값이 없는 항목만 params 에서 빠짐
    private ExplanationItem wellReviewedReason(BigDecimal positivePct, Integer reviewCount) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (positivePct != null) {
            params.put("positivePct", positivePct);
        }
        if (reviewCount != null) {
            params.put("reviewCount", reviewCount);
        }
        return new ExplanationItem("WELL_REVIEWED", params);
    }

    // 요청 세션 버킷과 실제 볼륨 버킷이 같을 때만 냄
    private void addVolumeFitsReason(List<ExplanationItem> reasons, SessionLength requestedSession, SessionLength actualSession) {
        if (requestedSession != null && requestedSession == actualSession) {
            reasons.add(new ExplanationItem("VOLUME_FITS", Map.of()));
        }
    }

    // 임베딩 분위기 경로로 뽑힌 게임에만 냄, 순위 산정과 최종 개수 자르기는 오케스트레이션 책임임
    private void addMoodMatchReason(List<ExplanationItem> reasons, boolean moodPath, int rank) {
        if (moodPath) {
            reasons.add(new ExplanationItem("MOOD_MATCH", Map.of("rank", rank)));
        }
    }

    // 요청 버킷과 실제 버킷이 둘 다 있고 서로 다를 때만 방향에 맞는 코드를 냄
    private void addVolumeMismatchCaveat(List<ExplanationItem> caveats, SessionLength requestedSession,
                                         SessionLength actualSession, BigDecimal positiveHour) {
        if (requestedSession == null || actualSession == null || requestedSession == actualSession) {
            return;
        }
        String code = actualSession.ordinal() > requestedSession.ordinal() ? "LONGER_THAN_REQUESTED" : "SHORTER_THAN_REQUESTED";
        caveats.add(new ExplanationItem(code, Map.of("typicalHours", positiveHour)));
    }

    // 파라미터 없는 주의점 공통 추가, 조건이 참일 때만 리스트에 넣음
    private void addIf(List<ExplanationItem> items, boolean condition, String code) {
        if (condition) {
            items.add(new ExplanationItem(code, Map.of()));
        }
    }
}
