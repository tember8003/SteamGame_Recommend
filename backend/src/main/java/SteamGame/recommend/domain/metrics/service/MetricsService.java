package SteamGame.recommend.domain.metrics.service;

import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.feedback.entity.FeedbackSignal;
import SteamGame.recommend.domain.feedback.repository.RecommendationFeedbackRepository;
import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.impression.repository.RecommendationImpressionRepository;
import SteamGame.recommend.domain.metrics.dto.MetricsOverviewDTO;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MetricsService {

    private final RecommendationImpressionRepository impressionRepository;
    private final RecommendationFeedbackRepository feedbackRepository;
    private final GameRepository gameRepository;

    public MetricsService(RecommendationImpressionRepository impressionRepository,
                          RecommendationFeedbackRepository feedbackRepository,
                          GameRepository gameRepository) {
        this.impressionRepository = impressionRepository;
        this.feedbackRepository = feedbackRepository;
        this.gameRepository = gameRepository;
    }

    public MetricsOverviewDTO overview(int topN) {
        long totalImpressions = impressionRepository.count();

        // context 별 노출수
        Map<FeedbackContext, Long> impressionByContext = new EnumMap<>(FeedbackContext.class);
        for (var row : impressionRepository.countByContext()) {
            impressionByContext.put(row.getContext(), row.getCount());
        }

        // context 별 signal 별 피드백수
        Map<FeedbackContext, Map<FeedbackSignal, Long>> feedbackByContext = new EnumMap<>(FeedbackContext.class);
        for (var row : feedbackRepository.countByContextAndSignal()) {
            feedbackByContext
                    .computeIfAbsent(row.getContext(), c -> new EnumMap<>(FeedbackSignal.class))
                    .put(row.getSignal(), row.getCount());
        }

        // 여섯 경로를 모두 채워 표가 항상 같은 행 수를 갖게 한다
        List<MetricsOverviewDTO.ContextMetric> contexts = new ArrayList<>();
        for (FeedbackContext context : FeedbackContext.values()) {
            long impressions = impressionByContext.getOrDefault(context, 0L);
            Map<FeedbackSignal, Long> signals = feedbackByContext.getOrDefault(context, Map.of());
            long up = signals.getOrDefault(FeedbackSignal.UP, 0L);
            long down = signals.getOrDefault(FeedbackSignal.DOWN, 0L);
            long click = signals.getOrDefault(FeedbackSignal.CLICK, 0L);
            double upRate = impressions > 0 ? (double) up / impressions : 0.0;
            contexts.add(new MetricsOverviewDTO.ContextMetric(context, impressions, up, down, click, upRate));
        }

        // 노출수 상위 게임에 이름을 붙인다, 빈 목록이면 게임 조회를 건너뛴다
        var topRows = impressionRepository.topAppids(PageRequest.of(0, topN));
        List<Long> appids = topRows.stream()
                .map(RecommendationImpressionRepository.AppidCount::getAppid)
                .toList();
        // 이름이 null 인 게임은 거른다, toMap 은 null 값에 NPE 를 내고 걸러도 appid 폴백으로 표시된다
        Map<Long, String> nameByAppid = appids.isEmpty()
                ? Map.of()
                : gameRepository.findByAppidIn(appids).stream()
                        .filter(g -> g.getName() != null)
                        .collect(Collectors.toMap(Game::getAppid, Game::getName, (a, b) -> a));
        List<MetricsOverviewDTO.TopGame> topGames = topRows.stream()
                .map(row -> new MetricsOverviewDTO.TopGame(
                        row.getAppid(),
                        nameByAppid.get(row.getAppid()),
                        row.getCount()))
                .toList();

        return new MetricsOverviewDTO(totalImpressions, contexts, topGames);
    }
}
