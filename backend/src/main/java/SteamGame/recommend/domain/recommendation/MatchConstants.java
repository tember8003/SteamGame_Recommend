package SteamGame.recommend.domain.recommendation;

import java.util.List;

public final class MatchConstants {
    private MatchConstants() {}
    // 코옵, 멀티 계열 태그, 이 중 하나라도 있으면 2인 이상 협동 가능으로 본다
    public static final List<String> COOP_TAGS = List.of(
            "Co-op", "Online Co-Op", "Local Co-Op", "Multiplayer", "Local Multiplayer", "MMO");
}
