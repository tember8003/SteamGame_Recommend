package SteamGame.recommend.domain.impression.repository;

import SteamGame.recommend.domain.impression.entity.RecommendationImpression;
import org.springframework.data.jpa.repository.JpaRepository;

// 노출 기록 저장을 맡는다, 저장은 saveAll
public interface RecommendationImpressionRepository extends JpaRepository<RecommendationImpression, Long> {
}
