package SteamGame.recommend.domain.library.analysis;

// 게임 하나의 리뷰 수와 긍정 비율(0~100)
public record ReviewRating(int totalReviews, double positivePct) {
}
