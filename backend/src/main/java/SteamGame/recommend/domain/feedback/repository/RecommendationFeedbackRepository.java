package SteamGame.recommend.domain.feedback.repository;

import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.feedback.entity.FeedbackSignal;
import SteamGame.recommend.domain.feedback.entity.RecommendationFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecommendationFeedbackRepository extends JpaRepository<RecommendationFeedback, Long> {

    // 특정 client_id, signal 의 appid 를 중복 없이 반환함, 억제와 취향 부스트가 이 조회를 공유함
    @Query("SELECT DISTINCT f.appid FROM RecommendationFeedback f " +
           "WHERE f.clientId = :clientId AND f.signal = :signal")
    List<Long> findAppidsByClientIdAndSignal(@Param("clientId") String clientId,
                                             @Param("signal") FeedbackSignal signal);

    // 지표 조회용 context 별 signal 별 피드백수 집계 프로젝션
    interface ContextSignalCount {
        FeedbackContext getContext();
        FeedbackSignal getSignal();
        long getCount();
    }

    @Query("SELECT f.context AS context, f.signal AS signal, COUNT(f) AS count " +
           "FROM RecommendationFeedback f GROUP BY f.context, f.signal")
    List<ContextSignalCount> countByContextAndSignal();
}
