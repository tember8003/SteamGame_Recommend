package SteamGame.recommend.domain.feedback.service;

import SteamGame.recommend.domain.feedback.entity.FeedbackSignal;
import SteamGame.recommend.domain.feedback.repository.RecommendationFeedbackRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 저장된 DOWN 피드백을 조회해 클라이언트가 보낸 제외 집합에 합침
// client_id 가 없거나 DOWN 이 없으면 원래 집합을 그대로 돌려주고, 반환은 항상 non-null 임
@Service
public class FeedbackSuppressionService {

    private final RecommendationFeedbackRepository repository;

    public FeedbackSuppressionService(RecommendationFeedbackRepository repository) {
        this.repository = repository;
    }

    // clientExcludes 는 null 이면 빈 집합으로 정규화함, findNonDuplicate 가 null 과 빈 집합을 같게 다뤄 차이 없음
    public Set<Long> resolveExcludes(String clientId, Set<Long> clientExcludes) {
        Set<Long> base = (clientExcludes != null) ? new HashSet<>(clientExcludes) : new HashSet<>();
        if (clientId == null || clientId.isBlank()) {
            return base;
        }
        List<Long> down = repository.findAppidsByClientIdAndSignal(clientId, FeedbackSignal.DOWN);
        base.addAll(down);
        return base;
    }
}
