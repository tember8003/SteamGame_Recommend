package SteamGame.recommend.domain.feedback.entity;

// 추천 반응 신호. CLICK 은 스토어 이동(약한 양성 후보)으로 UP 과 구분해 둔다
public enum FeedbackSignal {
    UP, DOWN, CLICK
}
