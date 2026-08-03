package SteamGame.recommend.domain.recommendation.dto;

import java.util.Map;

// 사유나 주의점 한 건을 코드와 파라미터로 나타냄, 문장을 굽지 않고 프런트가 언어별로 렌더링할 근거만 담음
public record ExplanationItem(String code, Map<String, Object> params) {
}
