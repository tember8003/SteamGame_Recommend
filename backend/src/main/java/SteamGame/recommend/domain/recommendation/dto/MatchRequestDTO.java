package SteamGame.recommend.domain.recommendation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Getter
@Setter
public class MatchRequestDTO {
    private String rawText;
    @Min(1) private Integer players;              // null 이면 인원 제약이 없고 값이 있으면 1 이상이어야 한다
    private SessionLength sessionLength;          // 잘못된 값은 역직렬화에서 걸러 400 으로 응답한다
    @PositiveOrZero private Integer budgetUsd;    // 예산 상한을 달러로 받는다
    private List<String> moodTags;
    private List<String> excludeTags;
    @Min(0) private int review;                   // 기존 플로우와 리뷰수 의미가 같다
    private Boolean korean;
    private Boolean free;
    private Set<Long> excludeAppids;
    @Size(max = 64) private String clientId;      // 익명 기기 로컬 키, 없으면 취향 부스트를 적용하지 않는다
}
