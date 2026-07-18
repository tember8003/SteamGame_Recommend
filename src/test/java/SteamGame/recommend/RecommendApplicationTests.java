package SteamGame.recommend;

import SteamGame.recommend.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "steam.api.key=test",
        "spring.ai.google.api-key=test"
})
class RecommendApplicationTests extends PostgresTestBase {

    @Test
    void contextLoads() {
    }
}
