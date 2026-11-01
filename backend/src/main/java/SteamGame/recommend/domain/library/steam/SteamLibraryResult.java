package SteamGame.recommend.domain.library.steam;

import java.util.List;

public sealed interface SteamLibraryResult {

    record Found(SteamProfile profile, List<OwnedGame> games) implements SteamLibraryResult {
    }

    // 프로필 또는 게임 세부 정보가 비공개
    record Private() implements SteamLibraryResult {
    }

    record NotFound() implements SteamLibraryResult {
    }

    record Empty() implements SteamLibraryResult {
    }

    // reason에는 닉네임과 게임 이름을 넣지 않음
    record Unavailable(String reason) implements SteamLibraryResult {
    }
}
