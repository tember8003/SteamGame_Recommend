package SteamGame.recommend.domain.recommendation.dto;

import SteamGame.recommend.domain.impression.entity.ImpressionSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SteamAppDTOJsonTest {

    // 프론트엔드 응답 계약이 바뀌지 않게 출처는 직렬화하지 않는다
    @Test
    void sourceIsNotSerialized() throws Exception {
        SteamAppDTO dto = new SteamAppDTO();
        dto.setAppid(1L);
        dto.setSource(ImpressionSource.GNN);

        String json = new ObjectMapper().writeValueAsString(dto);

        assertThat(json).contains("\"appid\":1").doesNotContain("source").doesNotContain("GNN");
    }
}
