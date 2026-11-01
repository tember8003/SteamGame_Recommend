package SteamGame.recommend.domain.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShareRequest(@NotBlank @Size(max = 64) String analysisId) {
}
