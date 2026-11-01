package SteamGame.recommend.domain.library.analysis;

// 장르 결과를 얼마나 믿을 수 있는지, 상수 이름이 공유 스냅샷과 응답에 그대로 나가므로 바꾸지 않는다
// READY는 1위 장르가 뚜렷함, MIXED는 1위와 2위 차이가 작음, INSUFFICIENT는 분류된 기록이 부족해 기본 거점을 씀
public enum GenreState { READY, MIXED, INSUFFICIENT }
