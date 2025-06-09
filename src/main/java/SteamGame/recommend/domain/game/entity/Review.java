package SteamGame.recommend.domain.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "reviews")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appid", nullable = false, unique = true)
    private Long appid;

    @Column(name = "total_reviews")
    private Integer totalReviews = 0;

    @Column(name = "positive_review_text", columnDefinition = "TEXT")
    private String positiveReviewText;

    @Column(name = "negative_review_text", columnDefinition = "TEXT")
    private String negativeReviewText;

    @Column(name = "review_score", length = 50)
    private String reviewScore;

    @Column(name = "positive_percentage", precision = 5, scale = 2)
    private BigDecimal positivePercentage;

    @Column(name = "positive_game_hour", precision = 7, scale = 2)
    private BigDecimal positiveGameHour;

    @Column(name = "negative_game_hour", precision = 7, scale = 2)
    private BigDecimal negativeGameHour;
}
