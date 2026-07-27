package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.SteamSpyAllEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SteamSpyParser {

    private final ObjectMapper objectMapper;

    public SteamSpyParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // SteamSpy ?request=all 응답(appid 키 맵)을 엔트리 리스트로 파싱, 파싱 실패 시 빈 리스트
    public List<SteamSpyAllEntry> parseAllPage(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            Map<String, SteamSpyAllEntry> map = objectMapper.readValue(
                    json, new TypeReference<Map<String, SteamSpyAllEntry>>() {});
            return new ArrayList<>(map.values());
        } catch (Exception e) {
            log.error("Failed to parse SteamSpy all-page response", e);
            return List.of();
        }
    }
}
