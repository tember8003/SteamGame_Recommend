package SteamGame.recommend.domain.game.repository;

import SteamGame.recommend.domain.game.entity.Game;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {
    @Query(value = """
        SELECT g.* FROM games g
        JOIN game_tags gt ON g.id = gt.game_id
        JOIN tags t ON t.id = gt.tag_id
        WHERE t.name IN :tagNames
            AND g.review_count >= :review
            AND (:korean_check IS NULL OR g.korean_support = :korean_check)
            AND (:free IS NULL OR g.is_free = :free)
        GROUP BY g.id
        HAVING COUNT(DISTINCT t.name) = :tagCount
        ORDER BY RAND()
        LIMIT 10
    """, nativeQuery = true)
    List<Game> findRandomGameByTags(@Param("tagNames") List<String> tagNames,
                                        @Param("tagCount") long tagCount, @Param("review") int review,
                                        @Param("korean_check") Boolean korean_check,
                                        @Param("free") Boolean free
    );

    //대소문자 구분없이 게임 찾기
    Optional<Game> findByNameIgnoreCase(String name);

}
