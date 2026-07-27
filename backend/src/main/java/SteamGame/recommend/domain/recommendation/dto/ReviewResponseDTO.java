package SteamGame.recommend.domain.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class ReviewResponseDTO {
    private String positiveReview;
    private String negativeReview;
    private BigDecimal negativeHours;
    private BigDecimal positiveHours;
    private String reviewSummary;
    private BigDecimal positiveRating;
}
