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

    // 아래 세 칼럼은 LIBRARY_COMPLETE 에만 채운다, 다른 이벤트는 DB CHECK 가 막는다
    @Column(name = "ledger_code", length = 32)
    private String ledgerCode;

    @Column(name = "crime_code", length = 32)
    private String crimeCode;

    @Column(name = "genre_code", length = 32)
    private String genreCode;

    public FunnelEvent(String clientId, FunnelEventType eventType, String ref, ShareChannel channel) {
        this.clientId = clientId;
        this.eventType = eventType;
        this.ref = ref;
        this.channel = channel;
    }

    // 라이브러리 분석 완료 이벤트, genreCode 는 기록이 부족하면 null 이다
    public static FunnelEvent libraryComplete(String clientId, String ledgerCode, String crimeCode, String genreCode) {
        FunnelEvent e = new FunnelEvent(clientId, FunnelEventType.LIBRARY_COMPLETE, null, null);
        e.ledgerCode = ledgerCode;
        e.crimeCode = crimeCode;
        e.genreCode = genreCode;
        return e;
    }
}
