package SteamGame.recommend.domain.library.dto;

// value가 null이면 근거 없음
public record GamerStatView(String code, Integer value, String label) {
}
