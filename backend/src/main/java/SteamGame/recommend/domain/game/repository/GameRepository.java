package SteamGame.recommend.domain.game.repository;

import SteamGame.recommend.domain.game.entity.Game;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {
    @Query(value = """
    SELECT g.* FROM games g
    JOIN game_tags gt ON g.id = gt.game_id
    JOIN tags t ON t.id = gt.tag_id
    INNER JOIN reviews r ON g.appid = r.appid 
    WHERE (:tagCount = 0 OR t.name IN :tagNames)
        AND r.total_reviews >= :review
        AND (:korean_check IS NULL OR g.korean_support = :korean_check)
        AND (:free IS NULL OR g.is_free = :free)
        AND (
            :excluded_check = false
            OR NOT EXISTS (
                SELECT 1
                FROM game_tags gt2
                JOIN tags t2 ON t2.id = gt2.tag_id
                WHERE gt2.game_id = g.id
                  AND t2.name IN (:excludedTag)
            )
        )
    GROUP BY g.id
    HAVING (:tagCount = 0 OR COUNT(DISTINCT t.name) = :tagCount)
    ORDER BY RANDOM()
    LIMIT 10
""", nativeQuery = true)
    List<Game> findRandomGameByTags(@Param("tagNames") List<String> tagNames,
                                        @Param("tagCount") long tagCount, @Param("review") int review,
                                        @Param("korean_check") Boolean korean_check,
                                        @Param("free") Boolean free,
                                        @Param("excluded_check") Boolean excluded_check,
                                        @Param("excludedTag") List<String> excludedTag);

    //Search for the game name case-insensitively
    Optional<Game> findByNameIgnoreCase(String name);

    Optional<Game> findByAppid(long appid);

    List<Game> findByNameStartingWithIgnoreCase(String prefix, Pageable pageable);

    @Query("SELECT g FROM Game g WHERE g.appid IN :appIds")
    List<Game> findByAppidIn(@Param("appIds") List<Long> appIds);

    @Query("""
    SELECT new map(g.appid as appId, COUNT(gt) as tagCount)
    FROM Game g
    JOIN GameTag gt ON g.id = gt.game.id
    WHERE g.appid IN :appIds
    GROUP BY g.appid""")
    List<Map<String, Object>> findTagCountsByAppids(@Param("appIds") List<Long> appids);

    @Query(value = """
    SELECT g.* FROM games g
    INNER JOIN reviews r ON g.appid = r.appid 
    WHERE r.total_reviews >= :review
        AND (:korean_check IS NULL OR g.korean_support = :korean_check)
        AND (:free IS NULL OR g.is_free = :free)
    ORDER BY RANDOM()
    LIMIT 50
""", nativeQuery = true)
    List<Game> findRandomGamesWithoutTags(@Param("review") int review,
                                          @Param("korean_check") Boolean korean_check,
                                          @Param("free") Boolean free);

    // 리뷰 맞히기 정답 후보, 긍정, 부정 리뷰 본문과 이미지가 모두 있는 게임만 무작위로 고른다
    // 50개는 7라운드에 필요한 정답 7개보다 넉넉하게 잡아, 보기 부족으로 건너뛰는 후보가 있어도 라운드를 채우려는 값이다
    @Query(value = """
    SELECT g.* FROM games g
    INNER JOIN reviews r ON g.appid = r.appid
    WHERE r.total_reviews >= :review
        AND r.positive_review_text IS NOT NULL AND r.positive_review_text <> ''
        AND r.negative_review_text IS NOT NULL AND r.negative_review_text <> ''
        AND g.image_url IS NOT NULL AND g.image_url <> ''
    ORDER BY RANDOM()
    LIMIT 50
""", nativeQuery = true)
    List<Game> findReviewGuessCandidates(@Param("review") int review);

    // 코사인 유사도 최근접 상위 k개. 하드 필터는 태그 매칭 대신 임베딩 유사도로 정렬.
    // 빈 exclude 집합은 excludeCount=0 가드로 NOT IN 을 무력화(sentinel [-1] 바인딩)
    @Query(value = """
    SELECT g.* FROM games g
    INNER JOIN reviews r ON g.appid = r.appid
    WHERE g.embedding IS NOT NULL
        AND r.total_reviews >= :review
        AND (:korean_check IS NULL OR g.korean_support = :korean_check)
        AND (:free IS NULL OR g.is_free = :free)
        AND (:excludeCount = 0 OR g.appid NOT IN (:excludeAppids))
        AND (
            :excluded_check = false
            OR NOT EXISTS (
                SELECT 1
                FROM game_tags gt2
                JOIN tags t2 ON t2.id = gt2.tag_id
                WHERE gt2.game_id = g.id
                  AND t2.name IN (:excludedTag)
            )
        )
    ORDER BY g.embedding <=> CAST(:qvec AS vector)
    LIMIT :k
""", nativeQuery = true)
    List<Game> findNearestByVector(@Param("qvec") String qvec,
                                   @Param("review") int review,
                                   @Param("korean_check") Boolean korean_check,
                                   @Param("free") Boolean free,
                                   @Param("excludeAppids") List<Long> excludeAppids,
                                   @Param("excludeCount") int excludeCount,
                                   @Param("excluded_check") boolean excluded_check,
                                   @Param("excludedTag") List<String> excludedTag,
                                   @Param("k") int k);

    // 예산과 코옵 하드필터를 더한 임베딩 최근접 정렬, 예산 지정 시 price가 null인 게임은 무료가 아니면 자동 제외됨
    // requireCoop=false면 coopTags에 sentinel(__none__)을 바인딩해 IN()이 비는 상황을 피함
    @Query(value = """
    SELECT g.* FROM games g
    INNER JOIN reviews r ON g.appid = r.appid
    WHERE g.embedding IS NOT NULL
        AND r.total_reviews >= :review
        AND (:korean IS NULL OR g.korean_support = :korean)
        AND (:free IS NULL OR g.is_free = :free)
        AND (:budgetCents IS NULL OR g.is_free = true OR g.price <= :budgetCents)
        AND (
            :requireCoop = false
            OR EXISTS (
                SELECT 1
                FROM game_tags gtc
                JOIN tags tc ON tc.id = gtc.tag_id
                WHERE gtc.game_id = g.id
                  AND tc.name IN (:coopTags)
            )
        )
        AND (:excludeCount = 0 OR g.appid NOT IN (:excludeAppids))
        AND (
            :excludedCheck = false
            OR NOT EXISTS (
                SELECT 1
                FROM game_tags gt2
                JOIN tags t2 ON t2.id = gt2.tag_id
                WHERE gt2.game_id = g.id
                  AND t2.name IN (:excludedTag)
            )
        )
    ORDER BY g.embedding <=> CAST(:qvec AS vector)
    LIMIT :k
""", nativeQuery = true)
    List<Game> findMatchNearest(@Param("qvec") String qvec,
                                 @Param("review") int review,
                                 @Param("korean") Boolean korean,
                                 @Param("free") Boolean free,
                                 @Param("budgetCents") Integer budgetCents,
                                 @Param("requireCoop") boolean requireCoop,
                                 @Param("coopTags") List<String> coopTags,
                                 @Param("excludeAppids") List<Long> excludeAppids,
                                 @Param("excludeCount") int excludeCount,
                                 @Param("excludedCheck") boolean excludedCheck,
                                 @Param("excludedTag") List<String> excludedTag,
                                 @Param("k") int k);

    // 예산과 코옵 하드필터를 더한 품질(리뷰 수) 정렬, 임베딩이 없는 게임도 후보에 포함됨
    // 예산, 코옵, 제외 가드는 findMatchNearest와 동일함
    @Query(value = """
    SELECT g.* FROM games g
    INNER JOIN reviews r ON g.appid = r.appid
    WHERE r.total_reviews >= :review
        AND (:korean IS NULL OR g.korean_support = :korean)
        AND (:free IS NULL OR g.is_free = :free)
        AND (:budgetCents IS NULL OR g.is_free = true OR g.price <= :budgetCents)
        AND (
            :requireCoop = false
            OR EXISTS (
                SELECT 1
                FROM game_tags gtc
                JOIN tags tc ON tc.id = gtc.tag_id
                WHERE gtc.game_id = g.id
                  AND tc.name IN (:coopTags)
            )
        )
        AND (:excludeCount = 0 OR g.appid NOT IN (:excludeAppids))
        AND (
            :excludedCheck = false
            OR NOT EXISTS (
                SELECT 1
                FROM game_tags gt2
                JOIN tags t2 ON t2.id = gt2.tag_id
                WHERE gt2.game_id = g.id
                  AND t2.name IN (:excludedTag)
            )
        )
    ORDER BY g.review_count DESC NULLS LAST
    LIMIT :k
""", nativeQuery = true)
    List<Game> findMatchByQuality(@Param("review") int review,
                                   @Param("korean") Boolean korean,
                                   @Param("free") Boolean free,
                                   @Param("budgetCents") Integer budgetCents,
                                   @Param("requireCoop") boolean requireCoop,
                                   @Param("coopTags") List<String> coopTags,
                                   @Param("excludeAppids") List<Long> excludeAppids,
                                   @Param("excludeCount") int excludeCount,
                                   @Param("excludedCheck") boolean excludedCheck,
                                   @Param("excludedTag") List<String> excludedTag,
                                   @Param("k") int k);

}
