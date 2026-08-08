package SteamGame.recommend.domain.game.mapper;

import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.recommendation.dto.ReviewResponseDTO;
import SteamGame.recommend.domain.game.entity.Review;
import org.springframework.stereotype.Component;

@Component
public class GameMapper {
    private final static String STEAM_STORE_URL = "https://store.steampowered.com/app/";

    public static SteamAppDTO convertToSteamDTO(Game game) {
        SteamAppDTO app = new SteamAppDTO();
        app.setName(cleanGameName(game.getName()));
        app.setAppid(game.getAppid());
        app.setShortDescription(game.getDescription());
        app.setHeaderImage(game.getImageUrl());
        app.setSteamStore(STEAM_STORE_URL + game.getAppid() + "?l=korean");
        return app;
    }

    public static ReviewResponseDTO convertToReviewDTO(Review review){
        ReviewResponseDTO dto = new ReviewResponseDTO();
        dto.setPositiveReview(review.getPositiveReviewText());
        dto.setPositiveHours(review.getPositiveGameHour());
        dto.setNegativeReview(review.getNegativeReviewText());
        dto.setNegativeHours(review.getNegativeGameHour());
        dto.setReviewSummary(review.getReviewScore());
        dto.setPositiveRating(review.getPositivePercentage());
        return dto;
    }

    // 이름은 정상 UTF-8 로 저장돼 인코딩 정리가 필요 없다, 앞뒤 공백만 다듬는다
    // 과거 치환(?→®, Â®→®, Â→"" 등)은 정상 문자를 파괴하던 H2 시절 유물이라 제거함, 다시 넣지 않는다
    private static String cleanGameName(String name) {
        if (name == null) return null;
        return name.trim();
    }
}
