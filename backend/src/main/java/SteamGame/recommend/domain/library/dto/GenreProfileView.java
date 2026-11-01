package SteamGame.recommend.domain.library.dto;

import SteamGame.recommend.domain.library.analysis.GenreState;

import java.util.List;

// 거점 배경용 장르 결과, 장르 기능 전에 저장된 공유에서는 응답 필드 자체가 null로 나간다
// distribution[].ratio는 분류된 시간 안에서의 비율, coverageRatio는 전체 플레이 시간 중 분류된 시간의 비율
// MIXED여도 secondaryGenre는 null일 수 있다, state는 반올림 전 값으로 정하므로 프론트가 비율로 다시 계산하지 않는다
public record GenreProfileView(
        String primaryGenre,
        String secondaryGenre,
        GenreState state,
        double coverageRatio,
        List<GenreShareView> distribution) {

    public record GenreShareView(String code, double ratio) {
    }
}
