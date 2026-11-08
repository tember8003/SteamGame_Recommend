package SteamGame.recommend.domain.metrics.service;

import SteamGame.recommend.domain.metrics.dto.MetricsStatusDTO;
import SteamGame.recommend.domain.metrics.repository.StatusQueries;
import SteamGame.recommend.exception.RecentErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

// 기동 시각, Flyway 버전, 최근 이벤트, 오류 기록기 스냅샷을 하나의 지금 상태 응답으로 묶는다
@Service
public class MetricsStatusService {

    private static final int RECENT_EVENT_LIMIT = 30;

    private final StatusQueries queries;
    private final RecentErrors recentErrors;
    private final ApplicationContext applicationContext;
    private final String revision;

    public MetricsStatusService(StatusQueries queries, RecentErrors recentErrors, ApplicationContext applicationContext,
                                @Value("${app.revision:unknown}") String revision) {
        this.queries = queries;
        this.recentErrors = recentErrors;
        this.applicationContext = applicationContext;
        this.revision = revision;
    }

    // 시각은 밀리초까지만 내보낸다, DB와 시계는 마이크로초까지 담는데 일부 브라우저는 소수 셋째 자리를 넘으면 날짜로 읽지 못함
    private static String iso(Instant t) {
        return t.truncatedTo(ChronoUnit.MILLIS).toString();
    }

    public MetricsStatusDTO status() {
        // 기동 시각은 RecentErrors에는 개념이 없어 컨텍스트에서 읽는다, 오류 기록의 since와도 같은 값을 쓴다
        String startedAt = Instant.ofEpochMilli(applicationContext.getStartupDate()).toString();

        MetricsStatusDTO.Server server = new MetricsStatusDTO.Server(revision, startedAt, queries.flywayVersion().orElse(null));

        List<MetricsStatusDTO.Event> events = queries.recentEvents(RECENT_EVENT_LIMIT).stream()
                .map(e -> new MetricsStatusDTO.Event(iso(e.createdAt()), e.eventType(), e.ref(), e.channel(),
                        e.ledgerCode(), e.crimeCode(), e.genreCode()))
                .toList();

        RecentErrors.Snapshot snapshot = recentErrors.snapshot();
        List<MetricsStatusDTO.CodeCount> counts = snapshot.counts().stream()
                .map(c -> new MetricsStatusDTO.CodeCount(c.status(), c.errorCode(), c.count()))
                .toList();
        List<MetricsStatusDTO.ErrorEntry> recent = snapshot.recent().stream()
                .map(e -> new MetricsStatusDTO.ErrorEntry(iso(e.at()), e.status(), e.errorCode(), e.route()))
                .toList();

        return new MetricsStatusDTO(server, events, new MetricsStatusDTO.Errors(startedAt, counts, recent));
    }
}
