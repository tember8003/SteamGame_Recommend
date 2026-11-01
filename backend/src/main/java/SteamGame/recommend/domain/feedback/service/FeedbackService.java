package SteamGame.recommend.domain.feedback.service;

import SteamGame.recommend.domain.feedback.dto.FeedbackRequestDTO;
import SteamGame.recommend.domain.feedback.entity.RecommendationFeedback;
import SteamGame.recommend.domain.feedback.repository.RecommendationFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {

    private final RecommendationFeedbackRepository repository;

    public FeedbackService(RecommendationFeedbackRepository repository) {
        this.repository = repository;
    }

    // 반응 하나를 새 기록으로 추가만 하고 고치거나 지우지 않는다, 같은 게임을 여러 번 눌러도 각각 신호로 남긴다
    @Transactional
    public void record(FeedbackRequestDTO dto) {
        RecommendationFeedback f = new RecommendationFeedback();
        f.setClientId(dto.getClientId());
        f.setSteamId(dto.getSteamId());
        f.setAppid(dto.getAppid());
        f.setContext(dto.getContext());
        f.setSignal(dto.getSignal());
        f.setRankPosition(dto.getRankPosition());
        repository.save(f);
    }
}
