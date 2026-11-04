package SteamGame.recommend.domain.game.repository;

import SteamGame.recommend.domain.taste.TasteDbTestConfig;
import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 라이브러리의 지금 해볼 게임이 쓰는 리뷰 평가 조회가 요청한 게임의 수와 긍정 비율만 돌려주는지 실제 DB로 확인한다
// 다른 DB 테스트와 같은 설정을 써서 스프링 컨텍스트를 새로 만들지 않고, 테스트 트랜잭션이 롤백되므로 정리는 필요 없다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TasteDbTestConfig.class)
class ReviewRatingQueryTest extends PostgresTestBase {

    private static final long BASE = 9_500_000L;

    @Autowired ReviewRepository reviewRepository;
    @Autowired JdbcTemplate jdbc;

    private void review(int n, int total, double pct) {
        jdbc.update("INSERT INTO reviews (appid, total_reviews, positive_percentage) VALUES (?, ?, ?)",
                BASE + n, total, pct);
    }

    @Test
    void returnsCountAndPositivePercentageForRequestedGamesOnly() {
        review(1, 500, 92.5);
        review(2, 40, 71);
        review(3, 9000, 99);

        List<ReviewRepository.Rating> rows = reviewRepository.findRatingsByAppidIn(List.of(BASE + 1, BASE + 2, BASE + 9));

        assertThat(rows).extracting(ReviewRepository.Rating::getAppid).containsExactlyInAnyOrder(BASE + 1, BASE + 2);
        ReviewRepository.Rating first = rows.stream().filter(r -> r.getAppid() == BASE + 1).findFirst().orElseThrow();
        assertThat(first.getTotalReviews()).isEqualTo(500);
        assertThat(first.getPositivePercentage().doubleValue()).isEqualTo(92.5);
    }
}
