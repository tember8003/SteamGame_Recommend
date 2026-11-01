package SteamGame.recommend.domain.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AnalyzeRequest(
        @NotBlank @Size(max = 200) String steamId,
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String clientId) {
}
