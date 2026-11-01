package SteamGame.recommend.domain.library.steam;

import java.time.Instant;

// 시간은 Steam이 준 분 단위 그대로, lastPlayed는 Steam 값이 0이면 null
public record OwnedGame(long appid, String name, int minutesTotal, int minutesTwoWeeks, Instant lastPlayed) {
}
