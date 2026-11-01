package SteamGame.recommend.domain.library.analysis;

// 장부 판정, 문구 코드와 이름이 같다
// 상수 이름은 저장 형식의 일부다: 공유 스냅샷과 library-v1.json의 문구 키에 그대로 들어가므로 바꾸지 않는다
public enum LedgerCode { STARTER, LEGENDARY_COLLECTOR, COLLECTOR, EXPLORER, PRACTICAL, BALANCED }
