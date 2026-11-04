package SteamGame.recommend.domain.taste.service;

// 한 축의 답변 비중이 한쪽으로 얼마나 기울었는지를 나타냄, 실력이나 정확도가 아니라 이번 답변의 비중임
public enum AxisBalance {
    BALANCED, LEANING, STRONG;

    // 높은 쪽 비율이 60 미만이면 BALANCED, 80 미만이면 LEANING, 그 이상이면 STRONG으로 나눈다
    public static AxisBalance of(int dominantPercent) {
        if (dominantPercent < 60) {
            return BALANCED;
        }
        return dominantPercent < 80 ? LEANING : STRONG;
    }
}
