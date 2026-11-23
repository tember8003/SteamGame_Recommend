package SteamGame.recommend.domain.recommendation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

// 예전 세션 길이 필드는 더 받지 않는다, 이전 프론트가 보내도 JacksonConfig 가 모르는 필드를 무시해 200 으로 처리됨
@Getter
@Setter
public class MatchRequestDTO {
    private String rawText;
    @Min(1) private Integer players;              // null 이면 인원 제약이 없고 값이 있으면 1 이상이어야 한다
    @PositiveOrZero private Integer budgetUsd;    // 예산 상한을 달러로 받는다
    private List<String> moodTags;
    private List<String> excludeTags;
    @Min(0) private int review;                   // 기존 플로우와 리뷰수 의미가 같다
    private Boolean korean;
    private Boolean free;
    private Set<Long> excludeAppids;
    @Size(max = 64) private String clientId;      // 익명 기기 로컬 키, 없으면 취향 부스트를 적용하지 않는다
}
