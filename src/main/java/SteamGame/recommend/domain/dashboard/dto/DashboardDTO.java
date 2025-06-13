package SteamGame.recommend.domain.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class DashboardDTO {
    private String steamId;
    private UserProfileSummary profileSummary;
    private List<TagPreferenceDTO> topTags;
    private List<GenreDistributionDTO> genreDistribution;
    private Recent2WeeksActivityDTO recentActivity;
    private GameLibraryStatsDTO libraryStats;
}


