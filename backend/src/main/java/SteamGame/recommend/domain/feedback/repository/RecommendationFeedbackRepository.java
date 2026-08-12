package SteamGame.recommend.domain.feedback.repository;

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
}
