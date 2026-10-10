package SteamGame.recommend.domain.impression.entity;

// 비슷한 게임 추천에서 그 게임을 고른 순위기
// GEMINI=설명문 임베딩, GNN=태그 구조 임베딩, TAG=태그 동시 출현 대체 경로
public enum ImpressionSource {
    GEMINI,
    GNN,
    TAG
}
