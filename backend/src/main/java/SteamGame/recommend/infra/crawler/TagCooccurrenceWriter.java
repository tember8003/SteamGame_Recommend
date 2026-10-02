package SteamGame.recommend.infra.crawler;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TagCooccurrenceWriter {

    private final JdbcTemplate jdbc;

    public TagCooccurrenceWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // CooccurrenceService 가 두 태그를 Java 문자열 비교로 정렬해 찾으므로 같은 순서로 저장해야 함
    // Postgres 기본 정렬은 대소문자를 Java 와 다르게 다뤄서 COLLATE "C" 로 바이트 순서를 씀
    // count 는 두 태그가 함께 붙은 게임 수를 셈
    private static final String INSERT_PAIRS = """
        INSERT INTO tag_cooccurrence (tag1, tag2, count)
        SELECT t1.name, t2.name, count(*)
        FROM game_tags a
        JOIN game_tags b ON b.game_id = a.game_id AND b.tag_id <> a.tag_id
        JOIN tags t1 ON t1.id = a.tag_id
        JOIN tags t2 ON t2.id = b.tag_id
        WHERE t1.name COLLATE "C" < t2.name COLLATE "C"
        GROUP BY t1.name, t2.name
        """;

    // 한 트랜잭션에서 지우고 다시 넣어, 다시 계산하는 동안 읽는 쪽은 커밋 전 데이터를 그대로 봄
    @Transactional
    public int replaceAll() {
        // 태그가 두 개 이상인 게임이 없으면 계산 결과가 비어 기존 쌍을 다 지우게 되므로 손대지 않고 멈춤
        Boolean hasPairs = jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM game_tags GROUP BY game_id HAVING count(*) >= 2)""", Boolean.class);
        if (!Boolean.TRUE.equals(hasPairs)) {
            throw new IllegalStateException("태그가 두 개 이상 붙은 게임이 없어 태그 쌍을 다시 계산하지 않음");
        }
        jdbc.update("DELETE FROM tag_cooccurrence");
        return jdbc.update(INSERT_PAIRS);
    }
}
