package SteamGame.recommend.domain.recommendation.similar;

import java.util.List;
import java.util.Locale;

// 같은 게임의 다른 판본을 같은 이름으로 모은다, 숫자는 지우지 않아 속편은 다른 이름으로 남는다
public final class NameNormalizer {

    // 끝에 붙는 판본 표시, 하나를 떼고 나서 또 붙어 있으면 다시 뗀다
    private static final List<String> EDITION_SUFFIXES = List.of(
            "game of the year edition",
            "definitive edition",
            "enhanced edition",
            "complete edition",
            "deluxe edition",
            "anniversary edition",
            "directors cut",
            "remastered",
            "goty");

    private NameNormalizer() {}

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String s = name.toLowerCase(Locale.ROOT)
                .replaceAll("['’]", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            for (String suffix : EDITION_SUFFIXES) {
                if (s.endsWith(" " + suffix)) {
                    s = s.substring(0, s.length() - suffix.length()).trim();
                    stripped = true;
                }
            }
        }
        return s;
    }
}
