package SteamGame.recommend.domain.tag.repository;

import SteamGame.recommend.domain.tag.entity.Tag;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

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
}
