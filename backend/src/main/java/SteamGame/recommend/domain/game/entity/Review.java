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

    // positive_game_hour, negative_game_hour 컬럼은 DB 에 남아 있지만 채우는 코드가 없어 전부 0 이라 매핑하지 않는다
    // 컬럼을 바로 지우지 않는 규칙이라 남겨 두며 ddl-auto=validate 는 매핑 안 된 컬럼을 검사하지 않는다
}
