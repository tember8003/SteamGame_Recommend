package SteamGame.recommend.domain.library.steam;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 사용자 입력(17자리 ID, 프로필 주소, 사용자 지정 주소 이름)을 SteamID64로 바꿈
@Component
public class SteamIdResolver {

    private static final Pattern STEAM_ID = Pattern.compile("7656\\d{13}");
    private static final Pattern DIGITS_ONLY = Pattern.compile("\\d+");
    private static final Pattern PROFILE_URL =
            Pattern.compile("(?:https?://)?(?:www\\.)?steamcommunity\\.com/profiles/(\\d+)/?(?:[?#].*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern VANITY_URL =
            Pattern.compile("(?:https?://)?(?:www\\.)?steamcommunity\\.com/id/([^/?#]+)/?(?:[?#].*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern VANITY_NAME = Pattern.compile("[A-Za-z0-9_-]{2,32}");

    private final Function<String, Optional<String>> vanityLookup;

    @Autowired
    public SteamIdResolver(SteamLibraryClient client) {
        this(client::resolveVanity);
    }

    // 테스트에서 Steam 호출 대신 조회기를 넣기 위한 생성자
    SteamIdResolver(Function<String, Optional<String>> vanityLookup) {
        this.vanityLookup = vanityLookup;
    }

    public String resolve(String input) {
        String text = input == null ? "" : input.trim();

        Matcher profile = PROFILE_URL.matcher(text);
        if (profile.matches()) {
            return requireSteamId(profile.group(1));
        }
        Matcher vanityUrl = VANITY_URL.matcher(text);
        if (vanityUrl.matches()) {
            return lookupVanity(vanityUrl.group(1));
        }
        // 숫자만 있는 입력은 이름이 아니라 ID로 보고 형식을 엄격히 검사함
        if (DIGITS_ONLY.matcher(text).matches()) {
            return requireSteamId(text);
        }
        return lookupVanity(text);
    }

    private static String requireSteamId(String digits) {
        if (!STEAM_ID.matcher(digits).matches()) {
            throw invalid();
        }
        return digits;
    }

    private String lookupVanity(String name) {
        if (!VANITY_NAME.matcher(name).matches()) {
            throw invalid();
        }
        return vanityLookup.apply(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "STEAM_PROFILE_NOT_FOUND"));
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_STEAM_ID");
    }
}
