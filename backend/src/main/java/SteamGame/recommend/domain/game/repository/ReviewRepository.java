package SteamGame.recommend.domain.game.repository;

import SteamGame.recommend.domain.game.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByAppidIn(List<Long> appids);

    // 라이브러리의 지금 해볼 게임용, 리뷰 본문 같은 큰 컬럼은 읽지 않고 수와 긍정 비율만 읽는다
    interface Rating {
        Long getAppid();

        Integer getTotalReviews();

        java.math.BigDecimal getPositivePercentage();
    }

    @Query("SELECT r.appid AS appid, r.totalReviews AS totalReviews, r.positivePercentage AS positivePercentage "
            + "FROM Review r WHERE r.appid IN :appids")
    List<Rating> findRatingsByAppidIn(@Param("appids") List<Long> appids);
}
