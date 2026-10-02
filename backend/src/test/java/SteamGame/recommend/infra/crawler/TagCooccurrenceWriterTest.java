package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.domain.recommendation.entity.TagPairKey;
import SteamGame.recommend.domain.recommendation.repository.CooccurrenceRepository;
import SteamGame.recommend.domain.recommendation.service.CooccurrenceService;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TagCooccurrenceWriter.class, CooccurrenceService.class})
class TagCooccurrenceWriterTest extends PostgresTestBase {

    @Autowired TagCooccurrenceWriter writer;
    @Autowired CooccurrenceService cooccurrenceService;
    @Autowired CooccurrenceRepository cooccurrenceRepository;
    @Autowired JdbcTemplate jdbc;

    // 다른 테스트가 남긴 행과 섞이지 않게 이 테스트 트랜잭션 안에서 비우고 시작함
    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM tag_cooccurrence");
        jdbc.update("DELETE FROM game_tags");
    }

    void tagGame(long appid, String... tagNames) {
        jdbc.update("INSERT INTO games (appid, name) VALUES (?, ?) ON CONFLICT (appid) DO NOTHING", appid, "G" + appid);
        Long gameId = jdbc.queryForObject("SELECT id FROM games WHERE appid = ?", Long.class, appid);
        for (String name : tagNames) {
            Long tagId = jdbc.queryForObject("""
                    INSERT INTO tags (name) VALUES (?)
                    ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id""", Long.class, name);
            jdbc.update("INSERT INTO game_tags (game_id, tag_id, votes) VALUES (?, ?, 1)", gameId, tagId);
        }
    }

    Integer count(String tag1, String tag2) {
        List<Integer> rows = jdbc.queryForList(
                "SELECT count FROM tag_cooccurrence WHERE tag1 = ? AND tag2 = ?", Integer.class, tag1, tag2);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Test
    void countsGamesSharingEachTagPair() {
        tagGame(990001, "CoA", "CoB", "CoC");
        tagGame(990002, "CoA", "CoB");
        tagGame(990003, "CoB", "CoC");

        int pairs = writer.replaceAll();

        assertThat(pairs).isEqualTo(3);
        assertThat(count("CoA", "CoB")).isEqualTo(2);
        assertThat(count("CoB", "CoC")).isEqualTo(2);
        assertThat(count("CoA", "CoC")).isEqualTo(1);
        assertThat(count("CoB", "CoA")).isNull();   // 한 쌍은 한 방향으로만 저장함
    }

    @Test
    void storesPairsInJavaStringOrder_soCooccurrenceServiceFindsMixedCaseTags() {
        // Java 비교로는 "RPG" < "Racing" 이지만 Postgres 기본 정렬로는 반대라 저장 방향이 갈림
        tagGame(990011, "Racing", "RPG");
        tagGame(990012, "Racing", "RPG");

        writer.replaceAll();

        assertThat("RPG".compareTo("Racing")).isNegative();
        assertThat(count("RPG", "Racing")).isEqualTo(2);
        Optional<TagPairKey> found = cooccurrenceService.findOptimalPairKey(List.of("Racing", "RPG"), 2);
        assertThat(found).isPresent();
        assertThat(found.get().getFirstTag()).isEqualTo("RPG");
        assertThat(found.get().getSecondTag()).isEqualTo("Racing");
    }

    @Test
    void rerun_replacesOldRows() {
        tagGame(990021, "CoA", "CoB");
        writer.replaceAll();
        jdbc.update("DELETE FROM game_tags");
        tagGame(990022, "CoC", "CoD");

        writer.replaceAll();

        assertThat(count("CoA", "CoB")).isNull();
        assertThat(count("CoC", "CoD")).isEqualTo(1);
        assertThat(cooccurrenceRepository.count()).isEqualTo(1);
    }

    @Test
    // 롤백이 아니라 지우기 전에 검사가 먼저 도는지를 확인함
    void noGameWithTwoTags_failsBeforeDeletingExistingRows() {
        tagGame(990031, "CoA", "CoB");
        writer.replaceAll();
        jdbc.update("DELETE FROM game_tags");
        tagGame(990032, "CoA");

        assertThatThrownBy(() -> writer.replaceAll()).isInstanceOf(IllegalStateException.class);
        assertThat(count("CoA", "CoB")).isEqualTo(1);
    }
}
