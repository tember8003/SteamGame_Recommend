package SteamGame.recommend.domain.taste.dto;

// 응답에 싣는 태그와 가중치, 문구 데이터 타입과 응답 모양을 떼어 놓는다
public record TagView(String tag, double weight) {
}
