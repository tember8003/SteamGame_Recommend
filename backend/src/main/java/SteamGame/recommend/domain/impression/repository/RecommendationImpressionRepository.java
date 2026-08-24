package SteamGame.recommend.domain.impression.repository;

import SteamGame.recommend.domain.impression.entity.RecommendationImpression;
import org.springframework.data.jpa.repository.JpaRepository;

// 노출 로그 적재 전용, 이번 슬라이스는 saveAll 만 쓴다
public interface RecommendationImpressionRepository extends JpaRepository<RecommendationImpression, Long> {
}
