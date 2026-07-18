package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.AppTag;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DetailUpsertRepository {

    private final JdbcTemplate jdbc;

    public DetailUpsertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // appid → games.id, 없으면 null
    public Long findGameId(long appid) {
        try {
            return jdbc.queryForObject("SELECT id FROM games WHERE appid = ?", Long.class, appid);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // 게임의 game_tags 를 주어진 가중 태그로 전량 교체(멱등), 시드 안 된 appid 는 no-op
    public void upsertTags(long appid, List<AppTag> tags) {
        Long gameId = findGameId(appid);
        if (gameId == null) return;
        jdbc.update("DELETE FROM game_tags WHERE game_id = ?", gameId);
        for (AppTag tag : tags) {
            Long tagId = upsertTag(tag.name());
            jdbc.update("""
                INSERT INTO game_tags (game_id, tag_id, votes) VALUES (?, ?, ?)
                ON CONFLICT (game_id, tag_id) DO UPDATE SET votes = EXCLUDED.votes""",
                gameId, tagId, tag.votes());
        }
    }

    // tags 를 name 유니크로 업서트하고 id 반환, DO UPDATE SET name=EXCLUDED.name 은 RETURNING 용 no-op
    private Long upsertTag(String name) {
        return jdbc.queryForObject("""
            INSERT INTO tags (name) VALUES (?)
            ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
            RETURNING id""", Long.class, name);
    }

    // 스토어페이지 확정 데이터를 games 행에 반영(appid 기준 UPDATE), 게임 행은 이미 존재해야 함
    public void upsertStorefront(SteamGame.recommend.infra.crawler.dto.StorefrontDetail d) {
        jdbc.update("""
            UPDATE games SET
                name = ?, description = ?, image_url = ?, is_free = ?, korean_support = ?,
                genres = ?, price = ?, release_date = ?, developer = ?, publisher = ?
            WHERE appid = ?""",
            d.name(), d.description(), d.imageUrl(), d.isFree(), d.koreanSupport(),
            d.genres(), d.price(), d.releaseDate() == null ? null : java.sql.Date.valueOf(d.releaseDate()),
            d.developer(), d.publisher(), d.appid());
    }

    // 리뷰 요약 + 샘플 텍스트를 reviews 에 업서트(appid UNIQUE 기준 ON CONFLICT)
    public void upsertReviewDetail(SteamGame.recommend.infra.crawler.dto.ReviewDetail d) {
        jdbc.update("""
            INSERT INTO reviews (appid, total_reviews, positive_percentage, review_score,
                                 positive_review_text, negative_review_text)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (appid) DO UPDATE SET
                total_reviews        = EXCLUDED.total_reviews,
                positive_percentage  = EXCLUDED.positive_percentage,
                review_score         = EXCLUDED.review_score,
                positive_review_text = EXCLUDED.positive_review_text,
                negative_review_text = EXCLUDED.negative_review_text""",
            d.appid(), d.totalReviews(), d.positivePercentage(), d.reviewScoreDesc(),
            d.positiveText(), d.negativeText());
    }

    // 다음 크롤링 대상 appid n개: 미크롤링 우선, 그 다음 오래된 순 → 인기 순
    public java.util.List<Long> selectNextToCrawl(int n) {
        return jdbc.queryForList("""
            SELECT appid FROM games
            ORDER BY last_crawled_at ASC NULLS FIRST, owners_estimate DESC NULLS LAST
            LIMIT ?""", Long.class, n);
    }

    // appid 의 last_crawled_at 을 갱신
    public void markCrawled(long appid, java.time.OffsetDateTime at) {
        jdbc.update("UPDATE games SET last_crawled_at = ? WHERE appid = ?", at, appid);
    }
}
