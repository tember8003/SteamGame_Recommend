// 그래프 확장(expand) 로직 중 순수 계산만 뽑아둔 모듈임
// cytoscape 인스턴스 없이도 좌표 시딩,엣지 중복 판정을 단위 테스트할 수 있게 분리함

export interface Point {
  x: number
  y: number
}

// 새로 추가하는 이웃 노드의 좌표를 소스 노드 주변에 흩어 시드한다
// 새 노드가 전부 같은 좌표(예: 0,0)에 있으면 cose 레이아웃이 서로 밀어내지 못해 한 점에 뭉치므로
// 소스 좌표에서 spread 범위만큼 무작위 오프셋을 준 좌표를 시작점으로 준다
// rand 는 기본값이 Math.random 이고, 테스트에서는 결정적인 함수를 주입해 검증한다
export function seedPosition(src: Point, spread = 300, rand: () => number = Math.random): Point {
  return {
    x: src.x + (rand() - 0.5) * spread,
    y: src.y + (rand() - 0.5) * spread,
  }
}

// 두 노드 id 로 엣지 id 를 만든다, source-target 순서 그대로 이어붙인다
export function edgeId(sourceId: string, targetId: string): string {
  return `${sourceId}-${targetId}`
}

// 이미 있는 엣지 id 집합에 정방향(source-target) 또는 역방향(target-source) 엣지가 있는지 확인한다
// 같은 두 노드 사이에 엣지를 두 번 추가하지 않기 위한 판정임
export function hasEdge(existingIds: ReadonlySet<string>, sourceId: string, targetId: string): boolean {
  return existingIds.has(edgeId(sourceId, targetId)) || existingIds.has(edgeId(targetId, sourceId))
}
