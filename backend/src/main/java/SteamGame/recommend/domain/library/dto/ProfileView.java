package SteamGame.recommend.domain.library.dto;

// steamId64는 새 게임 추천 호출용으로 분석 응답에만 싣는다, 공유 응답과 저장분에는 넣지 않는다
public record ProfileView(String personaName, String avatarUrl, String steamId64) {
}
