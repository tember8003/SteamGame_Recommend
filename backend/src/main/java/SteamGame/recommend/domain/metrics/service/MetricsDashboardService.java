package SteamGame.recommend.domain.metrics.service;

import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.library.analysis.CrimeCode;
import SteamGame.recommend.domain.library.analysis.LedgerCode;
import SteamGame.recommend.domain.library.content.LibraryContentValidator;
import SteamGame.recommend.domain.metrics.DashboardRange;
import SteamGame.recommend.domain.metrics.dto.MetricsDashboardDTO;
import SteamGame.recommend.domain.metrics.repository.DashboardQueries;
import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import SteamGame.recommend.domain.taste.content.TasteType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 영역별 집계를 하나의 응답으로 묶는다, 빈 날과 0회 코드를 채워 화면이 항상 같은 모양을 받게 한다
@Service
public class MetricsDashboardService {

    private static final int TOP_N = 20;
    private static final List<String> FEATURES = List.of("TASTE", "SIMILAR", "LIBRARY");
    private static final String INSUFFICIENT = "INSUFFICIENT";

    private final DashboardQueries queries;
    private final GameRepository gameRepository;
    private final TasteContentLoader tasteContent;
    private final Clock clock;

    @Autowired
    public MetricsDashboardService(DashboardQueries queries, GameRepository gameRepository, TasteContentLoader tasteContent) {
        this(queries, gameRepository, tasteContent, Clock.systemUTC());
    }

    MetricsDashboardService(DashboardQueries queries, GameRepository gameRepository, TasteContentLoader tasteContent,
                            Clock clock) {
        this.queries = queries;
        this.gameRepository = gameRepository;
        this.tasteContent = tasteContent;
        this.clock = clock;
    }

    // 여러 조회가 같은 시점을 보도록 REPEATABLE READ 로 묶는다, 기본 READ COMMITTED 는 문장마다 새로 읽음
    // 첫 이벤트 날은 전체 기간에만 필요하므로 그때만 조회한다
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MetricsDashboardDTO dashboard(DashboardRange range) {
        LocalDate first = range == DashboardRange.ALL ? queries.firstEventDate().orElse(null) : null;
        DashboardRange.Bounds b = range.bounds(clock, first);
        return new MetricsDashboardDTO(range.value(), b.fromDate().toString(), b.toDate().toString(),
                DashboardRange.ZONE.getId(), funnels(b), trend(b), distribution(b), recommendations(b), shares(b));
    }

    private MetricsDashboardDTO.Funnels funnels(DashboardRange.Bounds b) {
        DashboardQueries.Funnel f = queries.funnel(b);
        List<MetricsDashboardDTO.Step> taste = List.of(
                new MetricsDashboardDTO.Step("LANDING_VISIT", f.landing()),
                new MetricsDashboardDTO.Step("FEATURE_SELECT", f.tasteSelect()),
                new MetricsDashboardDTO.Step("TEST_START", f.testStart()),
                new MetricsDashboardDTO.Step("TEST_COMPLETE", f.testComplete()),
                new MetricsDashboardDTO.Step("SHARE_CLICK", f.tasteShare()));
        List<MetricsDashboardDTO.Step> library = List.of(
                new MetricsDashboardDTO.Step("LANDING_VISIT", f.landing()),
                new MetricsDashboardDTO.Step("FEATURE_SELECT", f.librarySelect()),
                new MetricsDashboardDTO.Step("LIBRARY_COMPLETE", f.libraryComplete()),
                new MetricsDashboardDTO.Step("SHARE_CLICK", f.libraryShare()));
        return new MetricsDashboardDTO.Funnels(taste, library);
    }

    // 이벤트가 없는 날도 0 으로 채운다
    private List<MetricsDashboardDTO.Daily> trend(DashboardRange.Bounds b) {
        Map<LocalDate, DashboardQueries.Daily> byDate = queries.trend(b).stream()
                .collect(Collectors.toMap(DashboardQueries.Daily::date, Function.identity()));
        List<MetricsDashboardDTO.Daily> out = new ArrayList<>();
        for (LocalDate d = b.fromDate(); !d.isAfter(b.toDate()); d = d.plusDays(1)) {
            DashboardQueries.Daily day = byDate.get(d);
            out.add(day == null
                    ? new MetricsDashboardDTO.Daily(d.toString(), 0, 0, 0)
                    : new MetricsDashboardDTO.Daily(d.toString(), day.visitors(), day.testCompletes(), day.libraryCompletes()));
        }
        return out;
    }

    private MetricsDashboardDTO.Distribution distribution(DashboardRange.Bounds b) {
        List<String> typeCodes = tasteContent.content().types().stream().map(TasteType::code).toList();
        List<MetricsDashboardDTO.CodeCount> genre = fill(LibraryContentValidator.GENRE_KEYS,
                queries.countLibraryCode("genre_code", b));
        genre = new ArrayList<>(genre);
        genre.add(new MetricsDashboardDTO.CodeCount(INSUFFICIENT, queries.countLibraryInsufficient(b)));
        return new MetricsDashboardDTO.Distribution(
                fill(FEATURES, queries.countByRef("FEATURE_SELECT", b)),
                fill(typeCodes, queries.countByRef("TEST_COMPLETE", b)),
                fill(Arrays.stream(LedgerCode.values()).map(Enum::name).toList(), queries.countLibraryCode("ledger_code", b)),
                fill(Arrays.stream(CrimeCode.values()).map(Enum::name).toList(), queries.countLibraryCode("crime_code", b)),
                List.copyOf(genre),
                queries.libraryRecordedSince().map(LocalDate::toString).orElse(null));
    }

    // 정해 둔 코드 순서로 0 회 코드까지 채운다, 목록에 없는 코드는 버린다
    private static List<MetricsDashboardDTO.CodeCount> fill(List<String> codes, Map<String, Long> counts) {
        return codes.stream().map(c -> new MetricsDashboardDTO.CodeCount(c, counts.getOrDefault(c, 0L))).toList();
    }

    private MetricsDashboardDTO.Recommendations recommendations(DashboardRange.Bounds b) {
        Map<String, Long> impressions = queries.impressionsByContext(b);
        Map<String, Map<String, Long>> signals = new HashMap<>();
        for (DashboardQueries.FeedbackCount f : queries.feedback(b)) {
            signals.computeIfAbsent(f.context(), k -> new HashMap<>()).put(f.signal(), f.count());
        }
        List<MetricsDashboardDTO.ContextMetric> contexts = new ArrayList<>();
        long total = 0;
        for (FeedbackContext c : FeedbackContext.values()) {
            long imp = impressions.getOrDefault(c.name(), 0L);
            Map<String, Long> s = signals.getOrDefault(c.name(), Map.of());
            long up = s.getOrDefault("UP", 0L);
            total += imp;
            contexts.add(new MetricsDashboardDTO.ContextMetric(c.name(), imp, up, s.getOrDefault("DOWN", 0L),
                    s.getOrDefault("CLICK", 0L), imp > 0 ? (double) up / imp : 0.0));
        }
        List<DashboardQueries.AppidCount> top = queries.topImpressions(b, TOP_N);
        List<Long> appids = top.stream().map(DashboardQueries.AppidCount::appid).toList();
        // 이름이 없는 게임은 거른다, 화면은 이름 대신 appid 를 보여 준다
        Map<Long, String> names = appids.isEmpty() ? Map.of() : gameRepository.findByAppidIn(appids).stream()
                .filter(g -> g.getName() != null)
                .collect(Collectors.toMap(Game::getAppid, Game::getName, (x, y) -> x));
        List<MetricsDashboardDTO.TopGame> topGames = top.stream()
                .map(t -> new MetricsDashboardDTO.TopGame(t.appid(), names.get(t.appid()), t.count())).toList();
        return new MetricsDashboardDTO.Recommendations(total, contexts, topGames);
    }

    private MetricsDashboardDTO.Shares shares(DashboardRange.Bounds b) {
        List<DashboardQueries.ShareCount> rows = queries.shares(b);
        return new MetricsDashboardDTO.Shares(shareStat(rows, "TASTE"), shareStat(rows, "LIBRARY"));
    }

    private static MetricsDashboardDTO.ShareStat shareStat(List<DashboardQueries.ShareCount> rows, String target) {
        long link = 0, image = 0, inflow = 0;
        for (DashboardQueries.ShareCount r : rows) {
            if (!r.target().equals(target)) {
                continue;
            }
            if (r.eventType().equals("SHARE_INFLOW")) {
                inflow += r.count();
            } else if ("LINK".equals(r.channel())) {
                link += r.count();
            } else if ("IMAGE".equals(r.channel())) {
                image += r.count();
            }
        }
        long clicks = link + image;
        return new MetricsDashboardDTO.ShareStat(link, image, inflow, clicks == 0 ? null : (double) inflow / clicks);
    }
}
