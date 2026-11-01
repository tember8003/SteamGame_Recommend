package SteamGame.recommend.domain.tag.repository;

import SteamGame.recommend.domain.taste.TasteDbTestConfig;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// 라이브러리 분석이 쓰는 게임 태그 조회가 서비스가 기대하는 키 이름과 값 타입을 실제로 돌려주는지 확인한다
// 다른 DB 테스트와 같은 설정을 써서 스프링 컨텍스트를 새로 만들지 않고, 테스트 트랜잭션이 롤백되므로 정리는 필요 없다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TasteDbTestConfig.class)
class TagRepositoryGameTagMappingTest extends PostgresTestBase {

    private static final long BASE = 9_400_000L;

    @Autowired TagRepository tagRepository;
    @Autowired JdbcTemplate jdbc;

    private void game(int n, String... tags) {
        long appid = BASE + n;
        jdbc.update("INSERT INTO games (appid, name, korean_support, is_free) VALUES (?,?,?,?)", appid, "g" + n, false, false);
        for (String tag : tags) {
            String name = "TM_" + tag;
            jdbc.update("INSERT INTO tags (name) VALUES (?) ON CONFLICT (name) DO NOTHING", name);
            jdbc.update("INSERT INTO game_tags (game_id, tag_id, votes) VALUES "
                            + "((SELECT id FROM games WHERE appid=?), (SELECT id FROM tags WHERE name=?), 1)",
                    appid, name);
        }
    }

    @Test
    void returnsAppIdAndTagNameRowsForTaggedGamesOnly() {
        game(1, "Strategy", "Cozy");
        game(2, "Strategy");
        game(3);

        List<Map<String, Object>> rows = tagRepository.findGameTagMapping(List.of(BASE + 1, BASE + 2, BASE + 3));

        assertThat(rows).hasSize(3);
        for (Map<String, Object> row : rows) {
            assertThat(row.keySet()).containsExactlyInAnyOrder("appId", "tagName");
            assertThat(row.get("appId")).isInstanceOf(Number.class);
            assertThat(row.get("tagName")).isInstanceOf(String.class);
        }
        Set<String> pairs = rows.stream()
                .map(r -> ((Number) r.get("appId")).longValue() + ":" + r.get("tagName"))
                .collect(Collectors.toSet());
        assertThat(pairs).containsExactlyInAnyOrder(
                (BASE + 1) + ":TM_Strategy", (BASE + 1) + ":TM_Cozy", (BASE + 2) + ":TM_Strategy");
        assertThat(rows).noneMatch(r -> ((Number) r.get("appId")).longValue() == BASE + 3);
    }

    @Test
    void ignoresAppIdsOutsideTheRequestedList() {
        game(1, "Strategy");
        game(2, "Cozy");

        List<Map<String, Object>> rows = tagRepository.findGameTagMapping(List.of(BASE + 2));

        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get("appId")).longValue()).isEqualTo(BASE + 2);
    }
}
