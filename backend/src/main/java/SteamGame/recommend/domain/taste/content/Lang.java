package SteamGame.recommend.domain.taste.content;

// 응답 언어
public enum Lang {
    KO, EN;

    // null, 빈 값, 모르는 값은 KO로 처리한다
    public static Lang from(String raw) {
        if (raw != null && raw.trim().equalsIgnoreCase("en")) {
            return EN;
        }
        return KO;
    }
}
