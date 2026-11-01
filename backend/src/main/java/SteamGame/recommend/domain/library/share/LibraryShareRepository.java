package SteamGame.recommend.domain.library.share;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class LibraryShareRepository {

    private final JdbcTemplate jdbc;

    public LibraryShareRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // payload 는 JSONB 라 문자열을 명시적으로 형변환해 넣는다
    public void insert(String shareId, String payloadJson, Instant expiresAt) {
        jdbc.update("INSERT INTO library_share(share_id, payload, expires_at) VALUES (?, CAST(? AS jsonb), ?)",
                shareId, payloadJson, Timestamp.from(expiresAt));
    }

    // 만료 시각은 DB 시계로 비교한다
    public Optional<String> findActive(String shareId) {
        return jdbc.query("SELECT payload::text FROM library_share WHERE share_id = ? AND expires_at > now()",
                (rs, i) -> rs.getString(1), shareId).stream().findFirst();
    }

    public int deleteExpired() {
        return jdbc.update("DELETE FROM library_share WHERE expires_at <= now()");
    }
}
