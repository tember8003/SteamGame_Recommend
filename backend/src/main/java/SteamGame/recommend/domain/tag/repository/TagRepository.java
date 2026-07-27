package SteamGame.recommend.domain.tag.repository;

import SteamGame.recommend.domain.tag.entity.Tag;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;

public interface TagRepository extends JpaRepository<Tag,Long> {
    @Query("""
      SELECT t.name
        FROM Tag t
        JOIN GameTag gt ON gt.tag.id = t.id
        JOIN Game g    ON gt.game.id = g.id
       WHERE g.appid IN :appids
    """)
    List<String> findTagNamesByAppIds(@Param("appids") List<Long> appids);

    Page<Tag> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    @Query("""
    SELECT new map(g.appid as appId, t.name as tagName)
    FROM Game g
    JOIN GameTag gt ON g.id = gt.game.id
    JOIN Tag t ON gt.tag.id = t.id
    WHERE g.appid IN :appIds
""")
    List<Map<String, Object>> findGameTagMapping(@Param("appIds") List<Long> appIds);

    @Query("""
    SELECT g.appid as appId, COUNT(t.id) as tagCount
    FROM Game g
    JOIN GameTag gt ON g.id = gt.game.id
    JOIN Tag t ON gt.tag.id = t.id
    WHERE g.appid IN :appIds
    GROUP BY g.appid
""")
    List<Object[]> findTagCountPerGameByAppIds(@Param("appIds") List<Long> appIds);

    @Query("""
    SELECT t.name as tagName, COUNT(DISTINCT g.id) as gameCount
    FROM Tag t
    JOIN GameTag gt ON t.id = gt.tag.id
    JOIN Game g ON gt.game.id = g.id
    WHERE g.appid IN :appIds
    GROUP BY t.id, t.name
    ORDER BY gameCount DESC
""")
    List<Object[]> findTagFrequencyByAppIds(@Param("appIds") List<Long> appIds);

    @Query("""
    SELECT g.appid as appId, t.name as tagName
    FROM Game g
    JOIN GameTag gt ON g.id = gt.game.id
    JOIN Tag t ON gt.tag.id = t.id
    WHERE g.appid IN :appIds
    ORDER BY g.appid, t.name
""")
    List<Object[]> findGameTagPairs(@Param("appIds") List<Long> appIds);

    @Query("""
    SELECT DISTINCT g.appid
    FROM Game g
    JOIN GameTag gt ON g.id = gt.game.id
    JOIN Tag t ON gt.tag.id = t.id
    WHERE t.name IN :tagNames
""")
    List<Long> findGameIdsByTagNames(@Param("tagNames") List<String> tagNames);

    @Query(value = """
    SELECT g.appid, t.name
    FROM games g
    JOIN game_tags gt ON g.id = gt.game_id
    JOIN tags t ON gt.tag_id = t.id
    WHERE g.appid IN :appIds
    AND (
        SELECT COUNT(*)
        FROM game_tags gt2
        JOIN tags t2 ON gt2.tag_id = t2.id
        WHERE gt2.game_id = g.id AND t2.name <= t.name
    ) <= :maxTagsPerGame
    ORDER BY g.appid, t.name
""", nativeQuery = true)
    List<Object[]> findTopTagsByAppIds(@Param("appIds") List<Long> appIds,
                                       @Param("maxTagsPerGame") int maxTagsPerGame);
}
