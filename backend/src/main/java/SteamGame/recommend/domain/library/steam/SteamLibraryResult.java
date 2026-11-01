package SteamGame.recommend.domain.library.steam;

import java.util.List;
import java.util.Objects;

public sealed interface SteamLibraryResult {

    record Found(SteamProfile profile, List<OwnedGame> games) implements SteamLibraryResult {

        // 캐시에 오래 남는 객체라 목록을 복사해 밖에서 바꾸지 못하게 한다
        public Found {
            Objects.requireNonNull(profile, "profile");
            games = List.copyOf(games);
        }
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

        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
