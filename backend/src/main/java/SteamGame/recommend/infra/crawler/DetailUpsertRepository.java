package SteamGame.recommend.infra.crawler;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DetailUpsertRepository {

    private final JdbcTemplate jdbc;

    public DetailUpsertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 리뷰 본문 두 칸만 씀, 요약 칸은 catalog 배치 소유라 건드리지 않음
    // 도움순 20개가 한쪽(예: 전부 긍정)으로만 오는 게임이 흔해서, 빈 쪽은 기존 본문을 지우지 않고 남김
    // text_updated_at 은 본문을 한쪽이라도 새로 쓴 시각이라, 대시보드가 작업 보고와 별개로 저장 여부를 세는 근거가 됨
    public void upsertReviewDetail(SteamGame.recommend.infra.crawler.dto.ReviewDetail d) {
        jdbc.update("""
            INSERT INTO reviews (appid, positive_review_text, negative_review_text, text_updated_at)
            VALUES (?, ?, ?, now())
            ON CONFLICT (appid) DO UPDATE SET
                positive_review_text = COALESCE(EXCLUDED.positive_review_text, reviews.positive_review_text),
                negative_review_text = COALESCE(EXCLUDED.negative_review_text, reviews.negative_review_text),
                text_updated_at = EXCLUDED.text_updated_at""",
            d.appid(), d.positiveText(), d.negativeText());
    }

    // 다음 크롤링 대상 appid n개: 미크롤링 우선, 그 다음 오래된 순 → 리뷰 많은 순
    public java.util.List<Long> selectNextToCrawl(int n) {
        return jdbc.queryForList("""
            SELECT appid FROM games
            ORDER BY last_crawled_at ASC NULLS FIRST, review_count DESC NULLS LAST
            LIMIT ?""", Long.class, n);
    }

    // appid 의 last_crawled_at 을 갱신
    public void markCrawled(long appid, java.time.OffsetDateTime at) {
        jdbc.update("UPDATE games SET last_crawled_at = ? WHERE appid = ?", at, appid);
    }
}
