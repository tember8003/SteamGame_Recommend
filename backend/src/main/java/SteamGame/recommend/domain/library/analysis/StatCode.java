package SteamGame.recommend.domain.library.analysis;

// 게이머 성향 지표 이름
// 상수 이름은 저장 형식의 일부다: 공유 스냅샷에 이름이 저장되고 library-v1.json에는 STAT_ 접두어를 붙인 키로 들어가므로 바꾸지 않는다
public enum StatCode { EXPLORATION, STRATEGY, COLLECTION, DIVERSITY, COOP }
