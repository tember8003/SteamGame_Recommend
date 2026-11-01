package SteamGame.recommend.domain.library.steam;

// 닉네임과 아바타는 본인 화면에만 쓰고 공유 저장에는 넣지 않음
public record SteamProfile(String steamId64, String personaName, String avatarUrl) {
}
