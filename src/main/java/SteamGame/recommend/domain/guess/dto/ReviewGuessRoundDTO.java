package SteamGame.recommend.domain.guess.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewGuessRoundDTO {
    private int roundIndex;
    private String positiveReview;
    private String negativeReview;
    private List<ReviewGuessGameDTO> gameOptions;  // 3개 선택지 (정답 + 2개)
    private Long correctAnswerId;   // 정답 게임 ID (클라이언트에서는 숨김)
}