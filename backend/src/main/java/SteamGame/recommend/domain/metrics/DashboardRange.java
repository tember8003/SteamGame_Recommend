package SteamGame.recommend.domain.metrics;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

// 대시보드 기간, 모두 한국 시간 자정으로 자른다
public enum DashboardRange {
    TODAY("today", 1), D7("7d", 7), D30("30d", 30), ALL("all", 0);

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final String value;
    private final int days;

    DashboardRange(String value, int days) {
        this.value = value;
        this.days = days;
    }

    public String value() {
        return value;
    }

    // null 이면 7일로 보고, 모르는 값이면 비운다(호출부가 400 으로 바꾼다)
    public static Optional<DashboardRange> parse(String raw) {
        if (raw == null) {
            return Optional.of(D7);
        }
        for (DashboardRange r : values()) {
            if (r.value.equals(raw)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    // from 은 포함, to 는 미포함이다, ALL 은 첫 이벤트 날부터이고 이벤트가 없으면 오늘부터다
    public Bounds bounds(Clock clock, LocalDate firstEventDate) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZONE);
        LocalDate today = now.toLocalDate();
        LocalDate fromDate;
        if (this == ALL) {
            fromDate = firstEventDate == null || firstEventDate.isAfter(today) ? today : firstEventDate;
        } else {
            fromDate = today.minusDays(days - 1L);
        }
        return new Bounds(fromDate.atStartOfDay(ZONE).toOffsetDateTime(), now.toOffsetDateTime(), fromDate, today);
    }

    public record Bounds(OffsetDateTime from, OffsetDateTime to, LocalDate fromDate, LocalDate toDate) {
    }
}
