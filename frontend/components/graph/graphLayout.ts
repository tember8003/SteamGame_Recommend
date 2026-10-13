// 그래프를 마인드맵처럼 펼치는 배치 계산만 뽑아둔 모듈임
// cytoscape 인스턴스 없이 좌표 계산,이웃 고르기,엣지 중복 판정을 단위 테스트할 수 있게 분리함

export interface Point {
  x: number
  y: number
}

// 중심을 둘러싼 원 위에 count 개를 같은 간격으로 놓는다, 첫 노드는 중심 바로 위에 두고 시계 방향으로 돈다
export function ringPositions(center: Point, count: number, radius: number): Point[] {
  return Array.from({ length: count }, (_, i) => {
    const angle = -Math.PI / 2 + (2 * Math.PI * i) / count
    return { x: center.x + radius * Math.cos(angle), y: center.y + radius * Math.sin(angle) }
  })
}

// origin 에서 angle 방향으로 distance 만큼 떨어진 곳에 count 개를 부채꼴로 놓는다
// 이웃한 노드끼리 각도를 step 만큼 벌리고, 전체가 angle 을 중심으로 대칭이 되게 한다
export function fanPositions(origin: Point, angle: number, count: number, distance: number, step: number): Point[] {
  return Array.from({ length: count }, (_, i) => {
    const a = angle + (i - (count - 1) / 2) * step
    return { x: origin.x + distance * Math.cos(a), y: origin.y + distance * Math.sin(a) }
  })
}

// from 에서 to 로 향하는 각도(라디안)를 돌려준다, 가지를 바깥으로 뻗을 방향을 정할 때 쓴다
export function angleBetween(from: Point, to: Point): number {
  return Math.atan2(to.y - from.y, to.x - from.x)
}

// 이웃 목록에서 새로 만들 노드 count 개를 순서대로 고른다
// 이미 화면에 있는 이웃은 노드를 다시 만들지 않고 선만 이을 대상(linked)으로 돌린다
// count 개를 채운 뒤의 이웃은 보지 않는다, 연결 대상도 count 개까지만 둔다, 더 이으면 선이 너무 많아진다
export function pickChildren(
  neighborIds: readonly string[],
  present: ReadonlySet<string>,
  selfId: string,
  count: number,
): { fresh: string[]; linked: string[] } {
  const fresh: string[] = []
  const linked: string[] = []
  const seen = new Set<string>([selfId])
  for (const id of neighborIds) {
    if (fresh.length >= count) break
    if (seen.has(id)) continue
    seen.add(id)
    if (present.has(id)) {
      if (linked.length < count) linked.push(id)
    } else {
      fresh.push(id)
    }
  }
  return { fresh, linked }
}

// 두 노드 id 로 엣지 id 를 만든다, source-target 순서 그대로 이어붙인다
export function edgeId(sourceId: string, targetId: string): string {
  return `${sourceId}-${targetId}`
}

// 이미 있는 엣지 id 집합에 정방향 또는 역방향 엣지가 있는지 확인한다
// 같은 두 노드 사이에 엣지를 두 번 추가하지 않기 위한 판정임
export function hasEdge(existingIds: ReadonlySet<string>, sourceId: string, targetId: string): boolean {
  return existingIds.has(edgeId(sourceId, targetId)) || existingIds.has(edgeId(targetId, sourceId))
}
