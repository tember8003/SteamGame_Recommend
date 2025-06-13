package SteamGame.recommend.domain.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor @RequiredArgsConstructor
public class CacheStatusResponse {
    private String steamId;
    private boolean cached;
    private String status;

}