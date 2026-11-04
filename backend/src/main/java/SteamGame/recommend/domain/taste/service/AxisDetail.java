package SteamGame.recommend.domain.taste.service;

// 한 축의 v2 계산 결과, 비율은 원점수로 계산하고 winner는 동점 규칙까지 적용한 글자를 담는다
// summaryKey는 보조 설명 문구를 찾을 때 쓰는 키를 담는다(예: E_LEANING, 6:6이면 EVEN)
public record AxisDetail(
        String id,
        String left,
        String right,
        int total,
        int leftPercent,
        int rightPercent,
        String winner,
        AxisBalance balance,
        boolean tieBroken,
        String summaryKey) {
}
