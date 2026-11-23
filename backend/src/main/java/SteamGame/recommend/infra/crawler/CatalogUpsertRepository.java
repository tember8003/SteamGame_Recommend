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
    // content_updated_at 은 Steam 이 직접 주는 이름과 설명이 실제로 달라질 때만 찍음, 재임베딩의 기준이라 매일 같은 값이 다시 들어와도 그대로 둠
    // 태그는 상위 20개 구성이 자주 바뀌어 넣으면 대량 재임베딩이 생기므로 뺌, 장르도 상위 20개 태그에서 가중치 순으로 뽑은 값이라 같은 이유로 뺌
    // 그래서 태그와 장르만 바뀐 게임은 임베딩 글의 Tags, Genres 줄이 다음 설명 변경 때까지 예전 값으로 남음, 태그 구조는 GNN 벡터가 따로 반영함
    // SET 의 games.칼럼은 갱신 전 값이라 새 값과 견줄 수 있음, 새 값은 다른 칸과 같은 COALESCE 규칙으로 계산함
    private static final String UPSERT_GAME = """
        INSERT INTO games (appid, name, description, image_url, is_free, korean_support, genres, price,
                           release_date, developer, publisher, review_count)
        VALUES (?, ?, ?, ?, ?, COALESCE(?::boolean, FALSE), ?, ?, ?, ?, ?, ?)
        ON CONFLICT (appid) DO UPDATE SET
            content_updated_at = CASE
                WHEN games.name IS DISTINCT FROM EXCLUDED.name
                  OR games.description IS DISTINCT FROM COALESCE(EXCLUDED.description, games.description)
                THEN now() ELSE games.content_updated_at END,
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
