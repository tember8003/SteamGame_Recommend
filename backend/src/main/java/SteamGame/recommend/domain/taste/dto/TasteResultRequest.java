package SteamGame.recommend.domain.taste.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

// answers 검증은 TasteScorer에 맡기고, 어떤 오류든 컨트롤러가 INVALID_ANSWERS 400으로 바꾼다
public record TasteResultRequest(
        @NotNull String version,
        List<String> answers,
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String clientId) {
}
