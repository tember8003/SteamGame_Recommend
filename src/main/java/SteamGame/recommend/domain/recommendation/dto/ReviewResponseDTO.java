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
    private String PositiveReview;
    private String NegativeReview;
    private BigDecimal NegativeHours;
    private BigDecimal PositiveHours;
    private String ReviewSummary;
    private BigDecimal PositiveRating;
}
