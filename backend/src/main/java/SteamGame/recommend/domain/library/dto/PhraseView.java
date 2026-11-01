package SteamGame.recommend.domain.library.dto;

import java.util.Map;

// text는 자리표시자가 들어 있는 원문, params가 채울 값
public record PhraseView(String code, String text, Map<String, Object> params) {
}
