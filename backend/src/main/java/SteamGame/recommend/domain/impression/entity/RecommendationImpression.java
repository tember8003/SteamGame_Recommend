package SteamGame.recommend.domain.impression.entity;

import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "recommendation_impression")
@Getter @Setter
@NoArgsConstructor
public class RecommendationImpression {

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

    @Column(name = "rank_position")
    private Integer rankPosition;

    // JPA 저장 시엔 @CreationTimestamp 가 값을 넣고, 원시 JDBC insert 는 DB DEFAULT now() 가 채운다
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // 비슷한 게임 추천에서만 채우고 다른 추천은 null 로 둔다
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 10)
    private ImpressionSource source;

    // id 와 createdAt 은 저장 시 생성되므로 받지 않는다
    public RecommendationImpression(String clientId, String steamId, Long appid,
                                    FeedbackContext context, Integer rankPosition) {
        this(clientId, steamId, appid, context, rankPosition, null);
    }

    public RecommendationImpression(String clientId, String steamId, Long appid,
                                    FeedbackContext context, Integer rankPosition, ImpressionSource source) {
        this.clientId = clientId;
        this.steamId = steamId;
        this.appid = appid;
        this.context = context;
        this.rankPosition = rankPosition;
        this.source = source;
    }
}
