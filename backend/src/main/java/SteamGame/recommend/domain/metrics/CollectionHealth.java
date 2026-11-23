package SteamGame.recommend.domain.metrics;

import SteamGame.recommend.domain.metrics.dto.MetricsStatusDTO;
import SteamGame.recommend.domain.metrics.repository.StatusQueries;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 수집 상태 경고 규칙, SQL 과 떼어 둬 시각과 숫자만으로 판정을 시험할 수 있게 한다
// 작업이 남긴 처리 수만 믿지 않고 실제로 DB 에 들어온 양과 견준다, 리뷰 엔드포인트가 막혔을 때 한 달간 성공으로 기록된 일이 있었음
public final class CollectionHealth {

    public static final String JOB_STUCK = "JOB_STUCK";
    public static final String JOB_STALE = "JOB_STALE";
    public static final String JOB_FAILED = "JOB_FAILED";
    public static final String CATALOG_DROP = "CATALOG_DROP";
    public static final String DETAIL_NO_WRITES = "DETAIL_NO_WRITES";

    static final String CATALOG = "steam_catalog";
    static final String DETAIL = "detail_rolling";

    // 하루 한 번 도는 작업은 실행 시간이 밀리는 만큼 2시간 여유를 둬 26시간으로 잡는다, 6시간마다 도는 상세 수집은 7시간
    // tag_embedding 은 아직 정기 실행이 없어 주기를 두지 않음, steamspy_seed 는 더 쓰지 않아 목록에서 뺀다
    public record JobSpec(String batchType, Integer expectedIntervalHours) {
    }

    public static final List<JobSpec> JOBS = List.of(
            new JobSpec(CATALOG, 26),
            new JobSpec("tag_cooccurrence", 26),
            new JobSpec(DETAIL, 7),
            new JobSpec("embed_backfill", 26),
            new JobSpec("tag_embedding", null));

    // 카탈로그 저장 수가 직전 성공보다 이 비율 넘게 줄면 경고한다
    private static final int CATALOG_DROP_PERCENT = 5;
    private static final Duration DETAIL_WRITE_WINDOW = Duration.ofHours(24);
    // 상세 수집은 30분, GNN 학습은 4시간쯤 걸린다, 이보다 오래 끝나지 않으면 프로세스가 죽었다고 본다
    private static final Duration STUCK_AFTER = Duration.ofHours(24);

    // note 에서 화면으로 내보낼 수 있는 키, 배치들이 남기는 집계 값만 둔다
    private static final Set<String> NOTE_KEYS = Set.of("pages", "stop", "skipped", "unknownTags", "pairs",
            "processed", "failed", "withText", "noText", "malformed", "games", "tags", "hubTags", "auc",
            "seconds", "cleared", "gate");
    private static final Pattern NOTE_TOKEN = Pattern.compile("([A-Za-z]+)=([A-Za-z0-9._,:-]+)");
    private static final String API_DOWN = "(api_down)";
    private static final String FAILED_PREFIX = "FAILED:";

    private CollectionHealth() {
    }

    public static List<String> batchTypes() {
        return JOBS.stream().map(JobSpec::batchType).toList();
    }

    // 모르는 작업 종류는 주기를 알 수 없으니 null 을 돌려준다
    public static Integer intervalOf(String batchType) {
        // 주기가 null 인 작업이 있어 Optional 로 감싸지 않고 직접 찾는다
        for (JobSpec j : JOBS) {
            if (j.batchType().equals(batchType)) {
                return j.expectedIntervalHours();
            }
        }
        return null;
    }

    // 주기가 있는 작업이 한 번도 성공하지 못했거나 마지막 성공이 주기보다 오래됐으면 주기 초과다
    public static boolean stale(Integer intervalHours, Instant lastSuccessAt, Instant now) {
        if (intervalHours == null) {
            return false;
        }
        if (lastSuccessAt == null) {
            return true;
        }
        return Duration.between(lastSuccessAt, now).compareTo(Duration.ofHours(intervalHours)) > 0;
    }

    // 마지막 기록이 시작된 지 24시간이 지나도록 끝나지 않았으면 멈춤으로 본다, 딱 24시간은 아직 아님
    // 주기가 없는 GNN 학습도 프로세스가 죽으면 끝난 시각이 비어 실행 중으로만 보이므로 따로 잡는다
    public static boolean stuck(Instant lastStartedAt, Instant lastFinishedAt, Instant now) {
        if (lastStartedAt == null || lastFinishedAt != null) {
            return false;
        }
        return Duration.between(lastStartedAt, now).compareTo(STUCK_AFTER) > 0;
    }

    // 배치가 실패 때 note 에 예외 메시지 원문을 남겨 그대로 내보내면 SQL 이나 내부 값이 브라우저로 샌다
    // 공백으로 나눈 조각 중 아는 키의 key=value 와 (api_down) 만 남기고, 원문이 FAILED: 로 시작하면 FAILED 를 앞에 붙인다
    // 남는 것이 없으면 null 을 돌려줘 화면이 '-' 를 보이게 함
    public static String safeNote(String raw) {
        if (raw == null) {
            return null;
        }
        List<String> kept = new ArrayList<>();
        if (raw.startsWith(FAILED_PREFIX)) {
            kept.add("FAILED");
        }
        for (String token : raw.trim().split("\\s+")) {
            if (token.equals(API_DOWN)) {
                kept.add(token);
                continue;
            }
            Matcher m = NOTE_TOKEN.matcher(token);
            if (m.matches() && NOTE_KEYS.contains(m.group(1))) {
                kept.add(token);
            }
        }
        return kept.isEmpty() ? null : String.join(" ", kept);
    }

    // 성공이 둘 있어야 견줄 수 있다, 곱셈으로 비교해 소수 반올림 오차를 피함
    public static boolean catalogDropped(Integer latestProcessed, Integer previousProcessed) {
        if (latestProcessed == null || previousProcessed == null) {
            return false;
        }
        return (long) latestProcessed * 100 < (long) previousProcessed * (100 - CATALOG_DROP_PERCENT);
    }

    // 성공이든 실패든 상세 수집이 24시간 안에 끝났다면 리뷰 본문이 적어도 하나는 들어와야 한다
    public static boolean detailNoWrites(Instant lastFinishedRunAt, long reviewTextWrites24h, Instant now) {
        if (lastFinishedRunAt == null) {
            return false;
        }
        return !lastFinishedRunAt.isBefore(now.minus(DETAIL_WRITE_WINDOW)) && reviewTextWrites24h == 0;
    }

    // 작업 순서대로 멈춤, 실패, 주기 초과를 내고 작업 하나에 묶인 데이터 경고를 뒤에 붙인다
    // embed_backfill 의 (api_down) 표기는 무료 할당량 때문에 매일 붙어 실패로 보지 않는다, FAILED: 접두어만 실패임
    public static List<MetricsStatusDTO.Warning> warnings(List<StatusQueries.JobSummary> jobs,
                                                          long reviewTextWrites24h, Instant now) {
        List<MetricsStatusDTO.Warning> out = new ArrayList<>();
        for (StatusQueries.JobSummary j : jobs) {
            if (stuck(j.lastStartedAt(), j.lastFinishedAt(), now)) {
                out.add(new MetricsStatusDTO.Warning(JOB_STUCK, j.batchType()));
            }
            if (j.lastFinishedRunFailed()) {
                out.add(new MetricsStatusDTO.Warning(JOB_FAILED, j.batchType()));
            }
            if (stale(intervalOf(j.batchType()), j.lastSuccessAt(), now)) {
                out.add(new MetricsStatusDTO.Warning(JOB_STALE, j.batchType()));
            }
        }
        for (StatusQueries.JobSummary j : jobs) {
            if (j.batchType().equals(CATALOG) && catalogDropped(j.lastSuccessProcessed(), j.previousSuccessProcessed())) {
                out.add(new MetricsStatusDTO.Warning(CATALOG_DROP, CATALOG));
            }
        }
        for (StatusQueries.JobSummary j : jobs) {
            if (j.batchType().equals(DETAIL) && detailNoWrites(j.lastFinishedRunAt(), reviewTextWrites24h, now)) {
                out.add(new MetricsStatusDTO.Warning(DETAIL_NO_WRITES, DETAIL));
            }
        }
        return out;
    }
}
