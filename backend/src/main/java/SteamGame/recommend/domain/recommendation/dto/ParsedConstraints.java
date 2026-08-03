package SteamGame.recommend.domain.recommendation.dto;

import java.util.List;

// 자연어 파싱 결과로 얻은 제약, 하드 제약과 분위기 텍스트를 함께 담는다
public record ParsedConstraints(
        Integer players,
        Integer budgetUsd,
        SessionLength sessionLength,
        Boolean needsCoop,
        String moodText,
        List<String> excludeTags) {
}
