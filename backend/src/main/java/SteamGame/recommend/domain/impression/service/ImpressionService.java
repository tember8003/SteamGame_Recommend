package SteamGame.recommend.domain.impression.service;

import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.impression.entity.RecommendationImpression;
import SteamGame.recommend.domain.impression.repository.RecommendationImpressionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ImpressionService {

    private static final Logger log = LoggerFactory.getLogger(ImpressionService.class);

    private final RecommendationImpressionRepository repository;

    public ImpressionService(RecommendationImpressionRepository repository) {
        this.repository = repository;
    }

    // 노출된 appid 를 노출 순서대로 1-based rank 를 매겨 적재한다
    // client_id 나 노출이 없으면 남기지 않고, 적재 실패는 삼켜 추천 응답에 영향을 주지 않는다
    @Async("impressionExecutor")
    public void record(String clientId, String steamId, FeedbackContext context, List<Long> appids) {
        if (clientId == null || clientId.isBlank() || appids == null || appids.isEmpty()) {
            return;
        }
        try {
            List<RecommendationImpression> rows = new ArrayList<>();
            int rank = 1;
            for (Long appid : appids) {
                rows.add(new RecommendationImpression(clientId, steamId, appid, context, rank));
                rank++;
            }
            repository.saveAll(rows);
        } catch (Exception e) {
            log.warn("노출 적재 실패 clientId={} context={} count={}", clientId, context, appids.size(), e);
        }
    }
}
