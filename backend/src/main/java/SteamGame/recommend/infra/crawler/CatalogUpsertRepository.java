package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.AppTag;
import SteamGame.recommend.infra.crawler.dto.StoreItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class CatalogUpsertRepository {

    private final JdbcTemplate jdbc;

    public CatalogUpsertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 응답에 빠질 수 있는 칸은 COALESCE 로 기존 값을 지킴
    // is_free 는 스팀이 유료 게임에서 필드를 빼므로 없으면 false 로 보고 그대로 덮음
    // 가격은 무료면 null 로 비우고 유료인데 값이 없으면 기존 값을 지킴
    // korean_support 는 언어 목록이 없어 null 이면 기존 값을 지키고 새 행은 false 로 넣음, 칼럼이 NOT NULL 이라 필요함
    // owners_estimate 는 스팀이 주지 않아 건드리지 않음
    private static final String UPSERT_GAME = """
        INSERT INTO games (appid, name, description, image_url, is_free, korean_support, genres, price,
                           release_date, developer, publisher, review_count)
        VALUES (?, ?, ?, ?, ?, COALESCE(?::boolean, FALSE), ?, ?, ?, ?, ?, ?)
        ON CONFLICT (appid) DO UPDATE SET
            name           = EXCLUDED.name,
            description    = COALESCE(EXCLUDED.description, games.description),
            image_url      = COALESCE(EXCLUDED.image_url, games.image_url),
            is_free        = EXCLUDED.is_free,
            korean_support = COALESCE(?::boolean, games.korean_support),
            genres         = COALESCE(EXCLUDED.genres, games.genres),
            price          = CASE WHEN EXCLUDED.is_free THEN NULL ELSE COALESCE(EXCLUDED.price, games.price) END,
            release_date   = COALESCE(EXCLUDED.release_date, games.release_date),
            developer      = COALESCE(EXCLUDED.developer, games.developer),
            publisher      = COALESCE(EXCLUDED.publisher, games.publisher),
            review_count   = EXCLUDED.review_count
        RETURNING id
        """;

    // 리뷰 본문 칸은 detail 배치 소유라 요약 세 칸만 씀
    private static final String UPSERT_REVIEW_SUMMARY = """
        INSERT INTO reviews (appid, total_reviews, positive_percentage, review_score)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (appid) DO UPDATE SET
            total_reviews       = EXCLUDED.total_reviews,
            positive_percentage = EXCLUDED.positive_percentage,
            review_score        = EXCLUDED.review_score
        """;

    @Transactional
    public void upsertItem(StoreItem s) {
        Long gameId = jdbc.queryForObject(UPSERT_GAME, Long.class,
                s.appid(), s.name(), s.description(), s.imageUrl(), s.isFree(), s.koreanSupport(),
                s.genres(), s.price(),
                s.releaseDate() == null ? null : java.sql.Date.valueOf(s.releaseDate()),
                s.developer(), s.publisher(), s.reviewCount(),
                s.koreanSupport());

        jdbc.update(UPSERT_REVIEW_SUMMARY, s.appid(), s.reviewCount(), s.positivePercentage(), s.reviewScore());

        // 태그가 비면 일시적 응답 누락일 수 있어 기존 태그를 지키려고 교체를 건너뜀
        if (s.tags().isEmpty()) return;

        // 한 문장 안에서 같은 행을 두 번 쓰면 오류가 나서 이름이 겹치면 앞의 것만 남김, 스팀은 가중치 큰 순으로 줌
        Map<String, Integer> byName = new LinkedHashMap<>();
        for (AppTag tag : s.tags()) byName.putIfAbsent(tag.name(), tag.votes());
        String[] names = byName.keySet().toArray(String[]::new);
        Integer[] votes = byName.values().toArray(Integer[]::new);

        // 게임 3만 개를 매일 도는 배치라 태그마다 SQL 을 보내지 않고 배열로 한 번에 씀
        // 이미 있는 태그 이름은 DO NOTHING 으로 넘겨 tags 테이블에 쓰기가 생기지 않게 함
        jdbc.update("DELETE FROM game_tags WHERE game_id = ?", gameId);
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO tags (name) SELECT unnest(?::text[]) ON CONFLICT (name) DO NOTHING");
            ps.setArray(1, con.createArrayOf("text", names));
            return ps;
        });
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO game_tags (game_id, tag_id, votes)
                SELECT ?, t.id, v.votes
                FROM unnest(?::text[], ?::int[]) AS v(name, votes)
                JOIN tags t ON t.name = v.name""");
            ps.setLong(1, gameId);
            ps.setArray(2, con.createArrayOf("text", names));
            ps.setArray(3, con.createArrayOf("integer", votes));
            return ps;
        });
    }
}
