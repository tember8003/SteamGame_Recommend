package SteamGame.recommend.domain.metrics.dto;

import java.util.List;

// 관리자 운영 상태 응답, 기간과 무관한 지금 상태만 담는다
public record MetricsStatusDTO(Server server, List<Event> recentEvents, Errors errors) {

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
}
