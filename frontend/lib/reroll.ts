// 다시추천(재조회) 시 이미 보여준 게임을 다음 요청에서 제외하기 위한 순수 로직만 모음
// 페이지 컴포넌트가 상태(누적 appid 배열)를 들고, 매 응답마다 이 함수로 갱신함

// 이전까지 누적된 appid 목록에 새로 받은 게임들의 appid 를 더해 중복 없이 반환함
// 순서는 먼저 본 게임이 앞에 오도록 유지함
export function mergeShownAppids(previous: number[], games: { appid: number }[]): number[] {
  const merged = new Set(previous);
  for (const game of games) {
    merged.add(game.appid);
  }
  return Array.from(merged);
}

// 요청에 실어 보낼 excludeAppids 값을 만듦, 누적 목록이 비어있으면 undefined 를 반환해
// 매번 excludeAppids: [] 를 보내지 않게 함
export function toExcludeAppids(shown: number[]): number[] | undefined {
  return shown.length > 0 ? shown : undefined;
}
