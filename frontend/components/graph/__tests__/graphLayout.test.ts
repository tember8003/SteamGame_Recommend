import { describe, expect, it } from 'vitest'
import { angleBetween, edgeId, fanPositions, hasEdge, pickChildren, ringPositions } from '../graphLayout'

function close(a: number, b: number) {
  expect(a).toBeCloseTo(b, 6)
}

describe('ringPositions', () => {
  it('첫 노드를 중심 바로 위에 두고 시계 방향으로 같은 간격으로 놓는다', () => {
    const points = ringPositions({ x: 0, y: 0 }, 4, 100)
    expect(points).toHaveLength(4)
    close(points[0].x, 0)
    close(points[0].y, -100)
    close(points[1].x, 100)
    close(points[1].y, 0)
    close(points[2].x, 0)
    close(points[2].y, 100)
    close(points[3].x, -100)
    close(points[3].y, 0)
  })

  it('중심 좌표만큼 옮겨서 놓는다', () => {
    const [top] = ringPositions({ x: 10, y: 20 }, 3, 50)
    close(top.x, 10)
    close(top.y, -30)
  })

  it('개수가 0 이면 빈 배열을 돌려준다', () => {
    expect(ringPositions({ x: 0, y: 0 }, 0, 100)).toEqual([])
  })
})

describe('fanPositions', () => {
  it('가운데 노드는 각도 방향으로 distance 만큼 떨어진 곳에 놓는다', () => {
    const points = fanPositions({ x: 0, y: 0 }, 0, 3, 100, 0.5)
    close(points[1].x, 100)
    close(points[1].y, 0)
  })

  it('양옆 노드는 각도를 step 만큼 벌려 대칭으로 놓는다', () => {
    const points = fanPositions({ x: 0, y: 0 }, 0, 3, 100, 0.5)
    close(points[0].x, 100 * Math.cos(-0.5))
    close(points[0].y, 100 * Math.sin(-0.5))
    close(points[2].x, 100 * Math.cos(0.5))
    close(points[2].y, 100 * Math.sin(0.5))
  })

  it('개수가 짝수여도 각도를 중심으로 대칭이다', () => {
    const points = fanPositions({ x: 0, y: 0 }, Math.PI / 2, 2, 100, 0.4)
    close(points[0].x, -points[1].x)
    close(points[0].y, points[1].y)
  })
})

describe('angleBetween', () => {
  it('from 에서 to 로 향하는 각도를 라디안으로 돌려준다', () => {
    close(angleBetween({ x: 0, y: 0 }, { x: 1, y: 0 }), 0)
    close(angleBetween({ x: 0, y: 0 }, { x: 0, y: 1 }), Math.PI / 2)
    close(angleBetween({ x: 5, y: 5 }, { x: 4, y: 5 }), Math.PI)
  })
})

describe('pickChildren', () => {
  it('화면에 없는 이웃을 순서대로 count 개까지 고른다', () => {
    const result = pickChildren(['1', '2', '3', '4'], new Set(), '0', 3)
    expect(result.fresh).toEqual(['1', '2', '3'])
    expect(result.linked).toEqual([])
  })

  it('이미 화면에 있는 이웃은 새로 만들지 않고 연결 대상으로 돌린다', () => {
    const result = pickChildren(['1', '2', '3', '4'], new Set(['2']), '0', 3)
    expect(result.fresh).toEqual(['1', '3', '4'])
    expect(result.linked).toEqual(['2'])
  })

  it('count 개를 채운 뒤의 이웃은 보지 않는다', () => {
    const result = pickChildren(['1', '2', '3', '4'], new Set(['4']), '0', 2)
    expect(result.fresh).toEqual(['1', '2'])
    expect(result.linked).toEqual([])
  })

  it('이미 화면에 있는 이웃이 많아도 연결 대상은 count 개까지만 돌린다', () => {
    const result = pickChildren(['1', '2', '3', '4', '5'], new Set(['1', '2', '3', '4']), '0', 2)
    expect(result.fresh).toEqual(['5'])
    expect(result.linked).toEqual(['1', '2'])
  })

  it('자기 자신과 같은 목록 안의 중복은 건너뛴다', () => {
    const result = pickChildren(['0', '1', '1', '2'], new Set(), '0', 3)
    expect(result.fresh).toEqual(['1', '2'])
    expect(result.linked).toEqual([])
  })
})

describe('edgeId', () => {
  it('source-target 순서로 id 를 만든다', () => {
    expect(edgeId('1', '2')).toBe('1-2')
  })
})

describe('hasEdge', () => {
  it('정방향 id 가 집합에 있으면 true 를 반환한다', () => {
    expect(hasEdge(new Set(['1-2']), '1', '2')).toBe(true)
  })

  it('역방향 id 만 있어도 true 를 반환한다', () => {
    expect(hasEdge(new Set(['2-1']), '1', '2')).toBe(true)
  })

  it('양방향 다 없으면 false 를 반환한다', () => {
    expect(hasEdge(new Set(['3-4']), '1', '2')).toBe(false)
  })
})
