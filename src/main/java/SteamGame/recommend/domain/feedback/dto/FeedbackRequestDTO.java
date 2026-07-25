package SteamGame.recommend.domain.feedback.dto;

import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.feedback.entity.FeedbackSignal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeedbackRequestDTO {

    @NotBlank
    @Size(max = 64)
    private String clientId;      // localStorage UUID

    @Size(max = 32)
    private String steamId;       // profile/recent 에서만, 선택

    @NotNull
    private Long appid;

    @NotNull
    private FeedbackContext context;   // 잘못된 값이면 역직렬화 단계에서 400

    @NotNull
    private FeedbackSignal signal;

    private Integer rankPosition;      // 선택
}
