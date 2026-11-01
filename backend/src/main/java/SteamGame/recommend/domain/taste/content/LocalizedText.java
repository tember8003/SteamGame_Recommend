package SteamGame.recommend.domain.taste.content;

// 한국어, 영어 문구 쌍
public record LocalizedText(String ko, String en) {
    public String get(Lang lang) {
        return lang == Lang.EN ? en : ko;
    }
}
