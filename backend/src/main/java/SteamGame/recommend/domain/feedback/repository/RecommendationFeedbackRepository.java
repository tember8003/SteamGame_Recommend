package SteamGame.recommend.domain.feedback.repository;

import SteamGame.recommend.domain.feedback.entity.RecommendationFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendationFeedbackRepository extends JpaRepository<RecommendationFeedback, Long> {
}
