package SteamGame.recommend.domain.metrics;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardRangeTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 한국 시간 2026-11-08 00:30 = UTC 2026-11-07 15:30
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T15:30:00Z"), ZoneOffset.UTC);

    @Test
    void parseAcceptsFourValuesAndDefaultsTo7d() {
        assertThat(DashboardRange.parse(null)).contains(DashboardRange.D7);
        assertThat(DashboardRange.parse("today")).contains(DashboardRange.TODAY);
        assertThat(DashboardRange.parse("7d")).contains(DashboardRange.D7);
        assertThat(DashboardRange.parse("30d")).contains(DashboardRange.D30);
        assertThat(DashboardRange.parse("all")).contains(DashboardRange.ALL);
        assertThat(DashboardRange.parse("7D")).isEmpty();
        assertThat(DashboardRange.parse("")).isEmpty();
        assertThat(DashboardRange.parse("1y")).isEmpty();
    }

    // UTC 로는 아직 11-07 이지만 한국 시간으로는 11-08 이므로 오늘은 11-08 이다
    @Test
    void todayStartsAtKoreanMidnight() {
        DashboardRange.Bounds b = DashboardRange.TODAY.bounds(CLOCK, null);
        assertThat(b.fromDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(b.toDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(b.from().toInstant()).isEqualTo(Instant.parse("2026-11-07T15:00:00Z"));
        assertThat(b.to().toInstant()).isEqualTo(Instant.parse("2026-11-07T15:30:00Z"));
    }

    @Test
    void sevenAndThirtyDaysIncludeToday() {
        assertThat(DashboardRange.D7.bounds(CLOCK, null).fromDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(DashboardRange.D30.bounds(CLOCK, null).fromDate()).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    void allStartsAtFirstEventDateOrToday() {
        assertThat(DashboardRange.ALL.bounds(CLOCK, LocalDate.of(2026, 11, 1)).fromDate())
                .isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(DashboardRange.ALL.bounds(CLOCK, null).fromDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(DashboardRange.ALL.bounds(CLOCK, LocalDate.of(2026, 11, 1)).from().atZoneSameInstant(KST).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 11, 1));
    }
}
