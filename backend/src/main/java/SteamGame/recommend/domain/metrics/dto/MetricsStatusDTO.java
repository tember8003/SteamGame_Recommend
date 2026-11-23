package SteamGame.recommend.domain.metrics.dto;

import java.util.List;

// 관리자 운영 상태 응답, 기간과 무관한 지금 상태만 담는다
public record MetricsStatusDTO(Server server, List<Event> recentEvents, Errors errors, Collection collection) {

    // flywayVersion은 버전 있는 성공 기록이 없으면 null로 둔다
    public record Server(String revision, String startedAt, String flywayVersion) {
    }

    // ledgerCode, crimeCode, genreCode는 LIBRARY_COMPLETE가 아니면 비어 있다, client_id는 담지 않는다
    public record Event(String at, String type, String ref, String channel, String ledgerCode, String crimeCode, String genreCode) {
    }

    public record CodeCount(int status, String errorCode, long count) {
    }

    public record ErrorEntry(String at, int status, String errorCode, String route) {
    }

    // since에는 기동 시각을 그대로 씀
    public record Errors(String since, List<CodeCount> counts, List<ErrorEntry> recent) {
    }

    // 배치 작업마다 작업이 스스로 남긴 기록(jobs)과 실제 DB에 들어온 양(data)을 나란히 둔다, 작업의 자기 보고만 믿지 않기 위함
    public record Collection(List<Job> jobs, Data data, List<Flag> flags, List<Warning> warnings) {
    }

    // last* 는 시작 시각이 가장 늦은 기록이다, 기록이 없으면 칸은 null 로, 횟수는 0 으로 둔다
    // lastNote 는 아는 집계 값만 남긴 것이다, 예외 메시지 원문은 싣지 않음
    // stuck 은 끝나지 않은 채 24시간을 넘긴 실행이다, 이때 running 은 false 로 둔다
    // lastRunFailed 는 마지막으로 끝난 실행 기준이라 실행 중이어도 직전 실패가 보인다
    // expectedIntervalHours 가 null 이면 정기 실행이 없는 작업이라 주기 초과로 보지 않음
    public record Job(String batchType, String lastStartedAt, String lastFinishedAt, Integer lastProcessed,
                      Integer lastFailed, String lastNote, boolean lastRunFailed, boolean running, boolean stuck,
                      String lastSuccessAt, Integer lastSuccessProcessed, Integer expectedIntervalHours,
                      boolean stale, int runs7d, int failedRuns7d) {
    }

    // tagVectorMissing 은 태그가 하나라도 있는데 GNN 벡터가 없는 게임 수다, 태그 없는 게임은 학습 대상이 아님
    public record Data(long games, long embeddingMissing, long tagVectorMissing, long reviewTextWrites24h,
                       String lastFunnelEventAt, String lastImpressionAt) {
    }

    public record Flag(String name, boolean enabled) {
    }

    // batchType 은 특정 작업에 묶이지 않는 경고면 null 이다
    public record Warning(String code, String batchType) {
    }
}
