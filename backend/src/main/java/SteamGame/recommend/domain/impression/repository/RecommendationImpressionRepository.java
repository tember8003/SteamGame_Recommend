package SteamGame.recommend.domain.impression.repository;

import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.impression.entity.RecommendationImpression;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

// 노출 로그 적재와 지표 조회를 겸한다, 적재는 saveAll, 조회는 아래 집계를 쓴다
public interface RecommendationImpressionRepository extends JpaRepository<RecommendationImpression, Long> {

    // 지표 조회용 노출수 집계 프로젝션
    interface ContextCount {
        FeedbackContext getContext();
        long getCount();
    }

    interface AppidCount {
        Long getAppid();
        long getCount();
    }

    // context 별 노출수
    @Query("SELECT i.context AS context, COUNT(i) AS count FROM RecommendationImpression i GROUP BY i.context")
    List<ContextCount> countByContext();

    // appid 별 노출수 내림차순, 상위 개수는 Pageable 로 제한한다
    @Query("SELECT i.appid AS appid, COUNT(i) AS count FROM RecommendationImpression i GROUP BY i.appid ORDER BY COUNT(i) DESC")
    List<AppidCount> topAppids(Pageable pageable);
}
