package SteamGame.recommend.domain.game.mapper;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameMapperTest {

    private Game named(String name) {
        Game g = new Game();
        g.setAppid(1L);
        g.setName(name);
        return g;
    }

    @Test
    void convertToSteamDTO_preservesRealQuestionMark() {
        // 유물 ?→® 치환이 정상 물음표 제목을 손상시키던 회귀 방지
        SteamAppDTO dto = GameMapper.convertToSteamDTO(named("Who's Your Daddy?!"));
        assertThat(dto.getName()).isEqualTo("Who's Your Daddy?!");
        assertThat(dto.getName()).doesNotContain("®");
    }

    @Test
    void convertToSteamDTO_preservesTrademarkAndRegistered() {
        SteamAppDTO dto = GameMapper.convertToSteamDTO(named("Warhammer 40,000™ / Rocket League®"));
        assertThat(dto.getName()).isEqualTo("Warhammer 40,000™ / Rocket League®");
    }

    @Test
    void convertToSteamDTO_doesNotStripLatin1Letters() {
        // 과거 .replace("Â","") 가 정당한 Â 를 지우던 것 방지
        SteamAppDTO dto = GameMapper.convertToSteamDTO(named("Âbenteuer"));
        assertThat(dto.getName()).isEqualTo("Âbenteuer");
    }

    @Test
    void convertToSteamDTO_trimsSurroundingWhitespace() {
        SteamAppDTO dto = GameMapper.convertToSteamDTO(named("  Hollow Knight  "));
        assertThat(dto.getName()).isEqualTo("Hollow Knight");
    }

    @Test
    void convertToSteamDTO_nullName_returnsNull() {
        SteamAppDTO dto = GameMapper.convertToSteamDTO(named(null));
        assertThat(dto.getName()).isNull();
    }
}
