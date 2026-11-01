package SteamGame.recommend.domain.event.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "funnel_event")
@Getter
@NoArgsConstructor
public class FunnelEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", length = 64)
    private String clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private FunnelEventType eventType;

    @Column(name = "ref", length = 32)
    private String ref;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 10)
    private ShareChannel channel;

    // 앱은 JPA 로 저장하므로 @CreationTimestamp 가 시각을 넣는다, 테스트나 수동 작업에서 SQL 로 직접 넣을 때는 DB 기본값 now() 가 채운다
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public FunnelEvent(String clientId, FunnelEventType eventType, String ref, ShareChannel channel) {
        this.clientId = clientId;
        this.eventType = eventType;
        this.ref = ref;
        this.channel = channel;
    }
}
