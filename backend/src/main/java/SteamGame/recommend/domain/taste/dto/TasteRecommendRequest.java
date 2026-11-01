package SteamGame.recommend.domain.taste.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

// review가 null이면 컨트롤러가 300으로 본다
public record TasteRecommendRequest(
        @NotBlank String code,
        @Min(0) Integer review,
        Boolean korean,
        Boolean free,
        @Size(max = 500) Set<Long> excludeAppids,
        @Size(max = 64) String clientId) {
}
