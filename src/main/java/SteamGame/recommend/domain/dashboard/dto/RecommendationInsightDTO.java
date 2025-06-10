package SteamGame.recommend.domain.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationInsightDTO {
    private String type; // "GENRE_EXPANSION", "SIMILAR_TASTE", "SEASONAL_TREND"
    private String title;
    private String description;
    private String actionText;
    private String actionUrl;
}
