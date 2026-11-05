package SteamGame.recommend.domain.library.dto;

import java.util.List;

// 라이브러리에서 고른 게임들, 그 기능 전에 저장된 공유에서는 응답 필드 자체가 null로 나간다
// 조건에 맞는 게임이 없으면 playNext와 revisit은 빈 목록, abandonedGenre는 null이다
public record PicksView(
        List<PlayNextView> playNext,
        List<RevisitView> revisit,
        AbandonedGenreView abandonedGenre) {

    public record PlayNextView(long appid, String name, List<String> matchedTags) {
    }

    public record RevisitView(long appid, String name, double hours, int idleDays) {
    }

    public record AbandonedGenreView(String genre, int gameCount, List<String> games) {
    }
}
