package SteamGame.recommend.domain.event.dto;

import SteamGame.recommend.domain.event.entity.FunnelEventType;
import SteamGame.recommend.domain.event.entity.ShareChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EventRequest(
        @NotNull FunnelEventType type,
        @Size(max = 32) String ref,
        ShareChannel channel,
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String clientId) {
}
