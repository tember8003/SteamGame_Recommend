package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.AppTag;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SteamSpyAppDetailsParser {

    private final ObjectMapper objectMapper;

    public SteamSpyAppDetailsParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // SteamSpy appdetails 응답의 tags(객체=태그맵, 빈배열=태그없음)를 안전 파싱, 실패 시 빈 리스트
    public List<AppTag> parseTags(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode tags = objectMapper.readTree(json).path("tags");
            if (!tags.isObject()) return List.of();   // "[]" (태그 없음) 또는 필드 부재
            List<AppTag> result = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> it = tags.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String name = e.getKey();
                if (name == null || name.isBlank()) continue;
                if (name.length() > 255) name = name.substring(0, 255);
                result.add(new AppTag(name, e.getValue().asInt(0)));
            }
            return result;
        } catch (Exception ex) {
            log.error("Failed to parse SteamSpy appdetails tags", ex);
            return List.of();
        }
    }
}
