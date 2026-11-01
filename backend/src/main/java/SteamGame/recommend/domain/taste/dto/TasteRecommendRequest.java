package SteamGame.recommend.domain.taste.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// review가 null이면 컨트롤러가 300으로 본다
public record TasteRecommendRequest(
        @NotBlank String code,
        @Min(0) Integer review,
        Boolean korean,
        Boolean free,
        @Size(max = 500) Set<Long> excludeAppids,
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String clientId) {

    // 제외 목록이 없거나 null 원소가 섞여 와도 뒤쪽 코드가 null을 신경 쓰지 않게 정리한다
    public TasteRecommendRequest {
        excludeAppids = excludeAppids == null
                ? Set.of()
                : excludeAppids.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }
}
