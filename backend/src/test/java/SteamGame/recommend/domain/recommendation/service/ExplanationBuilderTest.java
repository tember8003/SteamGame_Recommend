package SteamGame.recommend.domain.recommendation.service;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.entity.Review;
import SteamGame.recommend.domain.recommendation.dto.ExplanationItem;
import SteamGame.recommend.domain.recommendation.dto.MatchExplanation;
import SteamGame.recommend.domain.recommendation.dto.RecommendationCriteria;
import SteamGame.recommend.domain.recommendation.dto.SessionLength;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExplanationBuilderTest {

    ExplanationBuilder builder = new ExplanationBuilder();

    @Test
    void freeCoopGameProducesExpectedCodes() {
        Game g = game(true, 0, 152000);
        Review r = review(96, new BigDecimal("8"));
        RecommendationCriteria c = criteria(4, SessionLength.SHORT, 2000, "협동");

        MatchExplanation e = builder.build(g, r, List.of("Online Co-Op"), c, true, 1, false, null);

        assertThat(codes(e.reasons())).contains("IS_FREE", "COOP_SUPPORTED", "WELL_REVIEWED", "VOLUME_FITS", "MOOD_MATCH");
        assertThat(codes(e.caveats())).contains("PLAYER_COUNT_APPROX");
        assertThat(codes(e.caveats())).doesNotContain("PRICE_NO_DISCOUNT");
        assertThat(itemOf(e.reasons(), "COOP_SUPPORTED").params()).isEqualTo(Map.of("tag", "Online Co-Op"));
        assertThat(itemOf(e.reasons(), "MOOD_MATCH").params()).isEqualTo(Map.of("rank", 1));
    }

    @Test
    void moodUnavailableWithoutSessionSkipsVolumeUnknown() {
        Game g = game(false, 999, 10);
        Review r = review(90, null);
        RecommendationCriteria c = criteria(1, null, null, "cozy");

        MatchExplanation e = builder.build(g, r, List.of(), c, false, 1, false, null);

        assertThat(codes(e.caveats())).contains("MOOD_UNAVAILABLE");
        assertThat(codes(e.caveats())).doesNotContain("VOLUME_UNKNOWN");
        assertThat(codes(e.reasons())).doesNotContain("MOOD_MATCH");
    }

    @Test
    void sessionRequestedWithUnknownVolumeAddsCaveat() {
        Game g = game(false, 999, 500);
        Review r = review(80, null);
        RecommendationCriteria c = criteria(1, SessionLength.MEDIUM, null, null);

        MatchExplanation e = builder.build(g, r, List.of(), c, false, 1, false, null);

        assertThat(codes(e.caveats())).contains("VOLUME_UNKNOWN");
        assertThat(codes(e.caveats())).doesNotContain("MOOD_UNAVAILABLE");
    }

    @Test
    void volumeMismatchAddsLongerCaveat() {
        Game g = game(false, 999, 100);
        Review r = review(90, new BigDecimal("60"));
        RecommendationCriteria c = criteria(1, SessionLength.SHORT, null, null);

        MatchExplanation e = builder.build(g, r, List.of(), c, false, 1, false, null);

        assertThat(codes(e.caveats())).contains("LONGER_THAN_REQUESTED");
        assertThat(codes(e.caveats())).doesNotContain("SHORTER_THAN_REQUESTED");
        assertThat(codes(e.reasons())).doesNotContain("VOLUME_FITS");
        assertThat(itemOf(e.caveats(), "LONGER_THAN_REQUESTED").params()).isEqualTo(Map.of("typicalHours", new BigDecimal("60")));
    }

    @Test
    void volumeMismatchAddsShorterCaveat() {
        Game g = game(false, 999, 100);
        Review r = review(90, new BigDecimal("5"));
        RecommendationCriteria c = criteria(1, SessionLength.LONG, null, null);

        MatchExplanation e = builder.build(g, r, List.of(), c, false, 1, false, null);

        assertThat(codes(e.caveats())).contains("SHORTER_THAN_REQUESTED");
    }

    @Test
    void soloFriendlyReasonForSingleplayerTag() {
        Game g = game(false, 500, 300);
        Review r = review(85, new BigDecimal("20"));
        RecommendationCriteria c = criteria(1, null, null, null);

        MatchExplanation e = builder.build(g, r, List.of("Singleplayer"), c, false, 1, false, null);

        assertThat(codes(e.reasons())).contains("SOLO_FRIENDLY");
        assertThat(codes(e.caveats())).doesNotContain("PLAYER_COUNT_APPROX");
    }

    @Test
    void withinBudgetReasonAndPriceNoDiscountCaveat() {
        Game g = game(false, 1500, 50);
        Review r = review(70, null);
        RecommendationCriteria c = criteria(1, null, 2000, null);

        MatchExplanation e = builder.build(g, r, List.of(), c, false, 1, false, null);

        assertThat(codes(e.reasons())).contains("WITHIN_BUDGET");
        assertThat(codes(e.caveats())).contains("PRICE_NO_DISCOUNT");
        assertThat(itemOf(e.reasons(), "WITHIN_BUDGET").params()).isEqualTo(Map.of("priceCents", 1500, "budgetCents", 2000));
    }

    @Test
    void reviewRelaxedFlagAddsCaveat() {
        Game g = game(false, 500, 50);
        Review r = review(70, null);
        RecommendationCriteria c = criteria(1, null, null, null);

        MatchExplanation e = builder.build(g, r, List.of(), c, false, 1, true, null);

        assertThat(codes(e.caveats())).contains("REVIEW_RELAXED");
    }

    @Test
    void coopSupportedPicksFirstMatchingTagInGameOrder() {
        Game g = game(false, 999, 10);
        Review r = review(80, null);
        RecommendationCriteria c = criteria(2, null, null, null);

        MatchExplanation e = builder.build(g, r, List.of("Multiplayer", "Co-op"), c, false, 1, false, null);

        assertThat(itemOf(e.reasons(), "COOP_SUPPORTED").params()).isEqualTo(Map.of("tag", "Multiplayer"));
    }

    @Test
    void wellReviewedAlwaysAddedButOmitsNullParams() {
        Game g = game(false, 999, null);
        Review r = review(null, null);
        RecommendationCriteria c = criteria(1, null, null, null);

        MatchExplanation e = builder.build(g, r, List.of(), c, false, 1, false, null);

        assertThat(codes(e.reasons())).contains("WELL_REVIEWED");
        assertThat(itemOf(e.reasons(), "WELL_REVIEWED").params()).isEmpty();
    }

    @Test
    void likedGameNameAddsBecauseYouLikedReasonFirst() {
        Game g = game(false, 500, 300);
        Review r = review(85, new BigDecimal("20"));
        RecommendationCriteria c = criteria(1, null, null, null);

        MatchExplanation e = builder.build(g, r, List.of(), c, false, 1, false, "Hollow Knight");

        assertThat(e.reasons().get(0).code()).isEqualTo("BECAUSE_YOU_LIKED");
        assertThat(e.reasons().get(0).params()).isEqualTo(Map.of("gameName", "Hollow Knight"));
    }

    @Test
    void blankOrNullLikedGameNameOmitsBecauseYouLikedReason() {
        Game g = game(false, 500, 300);
        Review r = review(85, new BigDecimal("20"));
        RecommendationCriteria c = criteria(1, null, null, null);

        assertThat(codes(builder.build(g, r, List.of(), c, false, 1, false, null).reasons()))
                .doesNotContain("BECAUSE_YOU_LIKED");
        assertThat(codes(builder.build(g, r, List.of(), c, false, 1, false, "  ").reasons()))
                .doesNotContain("BECAUSE_YOU_LIKED");
    }

    private Game game(boolean free, Integer price, Integer reviewCount) {
        Game g = new Game();
        g.setFree(free);
        g.setPrice(price);
        g.setReviewCount(reviewCount);
        return g;
    }

    private Review review(Integer positivePct, BigDecimal positiveHour) {
        Review r = new Review();
        if (positivePct != null) {
            r.setPositivePercentage(BigDecimal.valueOf(positivePct));
        }
        r.setPositiveGameHour(positiveHour);
        return r;
    }

    private RecommendationCriteria criteria(Integer players, SessionLength session, Integer budgetCents, String moodText) {
        return RecommendationCriteria.builder()
                .players(players)
                .sessionLength(session)
                .budgetCents(budgetCents)
                .moodText(moodText)
                .build();
    }

    private List<String> codes(List<ExplanationItem> items) {
        return items.stream().map(ExplanationItem::code).toList();
    }

    private ExplanationItem itemOf(List<ExplanationItem> items, String code) {
        return items.stream().filter(i -> i.code().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("코드가 없음: " + code));
    }
}
