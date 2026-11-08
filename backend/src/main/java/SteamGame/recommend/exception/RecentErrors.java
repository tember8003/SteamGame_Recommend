package SteamGame.recommend.exception;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

// 예외 처리기가 돌려준 오류를 메모리에만 쌓는다, 재배포하면 초기화됨
// 여러 요청 스레드가 동시에 부르므로 횟수는 ConcurrentHashMap과 LongAdder로, 최근 목록은 synchronized 블록으로 지킨다
@Component
public class RecentErrors {

    private static final int MAX_RECENT = 30;

    private final Clock clock;
    private final Map<CodeKey, LongAdder> counts = new ConcurrentHashMap<>();
    private final ArrayDeque<Entry> recent = new ArrayDeque<>();

    public RecentErrors() {
        this(Clock.systemUTC());
    }

    RecentErrors(Clock clock) {
        this.clock = clock;
    }

    // 횟수를 올리고 최근 목록에도 넣는다
    public void record(int status, String errorCode, String routePattern) {
        count(status, errorCode);
        // 시각을 블록 안에서 읽어 최근 목록의 시각 순서가 뒤집히지 않게 한다
        synchronized (recent) {
            recent.addFirst(new Entry(clock.instant(), status, errorCode, routePattern));
            while (recent.size() > MAX_RECENT) {
                recent.removeLast();
            }
        }
    }

    // 횟수만 올린다, 경로 없음처럼 목록에 넣지 않을 때 쓴다
    public void count(int status, String errorCode) {
        counts.computeIfAbsent(new CodeKey(status, errorCode), key -> new LongAdder()).increment();
    }

    // 횟수와 최근 목록은 따로 읽으므로 잠깐 서로 다른 순간의 값일 수 있다, 화면 표시용이라 그대로 둔다
    public Snapshot snapshot() {
        List<CodeCount> codeCounts = counts.entrySet().stream()
                .map(e -> new CodeCount(e.getKey().status(), e.getKey().errorCode(), e.getValue().sum()))
                .sorted(Comparator.comparingLong(CodeCount::count).reversed()
                        .thenComparingInt(CodeCount::status)
                        .thenComparing(CodeCount::errorCode))
                .toList();
        List<Entry> recentCopy;
        synchronized (recent) {
            recentCopy = List.copyOf(recent);
        }
        return new Snapshot(codeCounts, recentCopy);
    }

    private record CodeKey(int status, String errorCode) {
    }

    public record CodeCount(int status, String errorCode, long count) {
    }

    public record Entry(Instant at, int status, String errorCode, String route) {
    }

    public record Snapshot(List<CodeCount> counts, List<Entry> recent) {
    }
}
