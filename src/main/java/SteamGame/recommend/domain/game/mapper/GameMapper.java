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
        app.setName(game.getName());
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
}
