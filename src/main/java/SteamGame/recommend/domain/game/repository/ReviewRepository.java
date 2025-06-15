package SteamGame.recommend.domain.game.repository;

import SteamGame.recommend.domain.game.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByAppidIn(List<Long> appids);
}
