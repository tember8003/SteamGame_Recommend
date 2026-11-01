package SteamGame.recommend.domain.library.content;

import SteamGame.recommend.domain.taste.content.LocalizedText;

import java.util.List;

// 문구 후보 하나, text 안의 {이름} 자리표시자는 params와 정확히 같아야 한다
public record Phrase(LocalizedText text, List<String> params) {

    public Phrase {
        params = params == null ? List.of() : List.copyOf(params);
    }
}
