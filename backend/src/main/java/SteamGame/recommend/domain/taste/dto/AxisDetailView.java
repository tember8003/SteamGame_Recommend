package SteamGame.recommend.domain.taste.dto;

// v2 결과의 축 하나, summary는 요청 언어의 보조 설명 문구를 담는다
// 비율은 이번 답변 점수의 비중이며 실력이나 정확도를 뜻하지 않음
public record AxisDetailView(
        String id,
        String left,
        String right,
        int total,
        int leftPercent,
        int rightPercent,
        String winner,
        String balance,
        boolean tieBroken,
        String summary) {
}
