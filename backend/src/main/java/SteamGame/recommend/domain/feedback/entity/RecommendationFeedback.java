package SteamGame.recommend.domain.feedback.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "recommendation_feedback")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "steam_id", length = 32)
    private String steamId;

    @Column(name = "appid", nullable = false)
    private Long appid;

    @Enumerated(EnumType.STRING)
    @Column(name = "context", nullable = false, length = 20)
    private FeedbackContext context;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal", nullable = false, length = 10)
    private FeedbackSignal signal;

    @Column(name = "rank_position")
    private Integer rankPosition;

    // 앱은 JPA 로 저장하므로 @CreationTimestamp 가 시각을 넣는다, 테스트나 수동 작업에서 SQL 로 직접 넣을 때는 DB 기본값 now() 가 채운다
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
