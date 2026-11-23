package SteamGame.recommend.domain.metrics.service;

import SteamGame.recommend.domain.metrics.CollectionHealth;
import SteamGame.recommend.domain.metrics.dto.MetricsStatusDTO;
import SteamGame.recommend.domain.metrics.repository.StatusQueries;
import SteamGame.recommend.domain.recommendation.similar.RecommendProperties;
import SteamGame.recommend.exception.RecentErrors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

// 기동 시각, Flyway 버전, 최근 이벤트, 오류 기록기 스냅샷, 수집 상태를 하나의 지금 상태 응답으로 묶는다
@Service
public class MetricsStatusService {

    private static final int RECENT_EVENT_LIMIT = 30;

    private final StatusQueries queries;
    private final RecentErrors recentErrors;
    private final ApplicationContext applicationContext;
    private final RecommendProperties recommendProperties;
    private final String revision;
    private final Clock clock;

    @Autowired
    public MetricsStatusService(StatusQueries queries, RecentErrors recentErrors, ApplicationContext applicationContext,
                                RecommendProperties recommendProperties,
                                @Value("${app.revision:unknown}") String revision) {
        this(queries, recentErrors, applicationContext, recommendProperties, revision, Clock.systemUTC());
    }

    MetricsStatusService(StatusQueries queries, RecentErrors recentErrors, ApplicationContext applicationContext,
                         RecommendProperties recommendProperties, String revision, Clock clock) {
        this.queries = queries;
        this.recentErrors = recentErrors;
        this.applicationContext = applicationContext;
        this.recommendProperties = recommendProperties;
        this.revision = revision;
        this.clock = clock;
    }

    // 시각은 밀리초까지만 내보낸다, DB와 시계는 마이크로초까지 담는데 일부 브라우저는 소수 셋째 자리를 넘으면 날짜로 읽지 못함
    private static String iso(Instant t) {
        return t.truncatedTo(ChronoUnit.MILLIS).toString();
    }

    // 기록이 없어 비어 있는 시각은 null 로 그대로 내보낸다
    private static String isoOrNull(Instant t) {
        return t == null ? null : iso(t);
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

        return new MetricsStatusDTO(server, events, new MetricsStatusDTO.Errors(startedAt, counts, recent), collection());
    }

    // 기준 시각은 DB 의 now() 대신 주입한 시계에서 계산한다, 주기 초과 판정과 같은 순간을 보게 하고 시험에서 고정할 수 있게 함
    private MetricsStatusDTO.Collection collection() {
        Instant now = clock.instant();
        List<StatusQueries.JobSummary> summaries =
                queries.jobSummaries(CollectionHealth.batchTypes(), now.minus(Duration.ofDays(7)));
        StatusQueries.CollectionData d = queries.collectionData(now.minus(Duration.ofHours(24)));

        List<MetricsStatusDTO.Job> jobs = summaries.stream().map(j -> {
            Integer interval = CollectionHealth.intervalOf(j.batchType());
            boolean stuck = CollectionHealth.stuck(j.lastStartedAt(), j.lastFinishedAt(), now);
            // 실패 판단은 원문 note 로 이미 끝났으므로 내보낼 note 만 걸러 낸다
            return new MetricsStatusDTO.Job(j.batchType(), isoOrNull(j.lastStartedAt()), isoOrNull(j.lastFinishedAt()),
                    j.lastProcessed(), j.lastFailed(), CollectionHealth.safeNote(j.lastNote()), j.lastFinishedRunFailed(),
                    j.running() && !stuck, stuck,
                    isoOrNull(j.lastSuccessAt()), j.lastSuccessProcessed(), interval,
                    CollectionHealth.stale(interval, j.lastSuccessAt(), now), j.runs7d(), j.failedRuns7d());
        }).toList();
        MetricsStatusDTO.Data data = new MetricsStatusDTO.Data(d.games(), d.embeddingMissing(), d.tagVectorMissing(),
                d.reviewTextWrites(), isoOrNull(d.lastFunnelEventAt()), isoOrNull(d.lastImpressionAt()));
        // 설정값은 기동 때 정해져 바뀌지 않지만, 꺼져 있는 것이 의도인지 화면에서 바로 확인하려고 함께 싣는다
        List<MetricsStatusDTO.Flag> flags = List.of(
                new MetricsStatusDTO.Flag("similarInterleave", recommendProperties.isSimilarInterleaveEnabled()));

        return new MetricsStatusDTO.Collection(jobs, data, flags,
                CollectionHealth.warnings(summaries, d.reviewTextWrites(), now));
    }
}
