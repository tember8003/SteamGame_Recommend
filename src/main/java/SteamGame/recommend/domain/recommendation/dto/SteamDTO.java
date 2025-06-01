package SteamGame.recommend.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

public class SteamDTO {

    @Getter @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SteamAppListResponse {
        private AppList applist;
    }

    @Getter @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppList {
        private List<SteamApp> apps;
    }

    @Getter @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SteamApp {
        private Long appid;
        private String name;
        private String shortDescription;
        private String headerImage;
        private String steamStore;
    }

    //태그와 게임 정보 함께 반환하기 -> 우선 SteamDTO에 함께 둠.
    @AllArgsConstructor @NoArgsConstructor
    @Getter @Setter
    public static class RecommendationResult {
        private List<String> usedTags;
        private List<SteamApp> recommendedGame;
    }
}