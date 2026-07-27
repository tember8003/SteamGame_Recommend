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

    // 단건 append-only 적재(중복 클릭도 신호로 보존)
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
