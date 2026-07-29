import { describe, expect, it } from 'vitest'
import { edgeId, hasEdge, seedPosition } from '../seedPosition'

describe('seedPosition', () => {
  it('rand 가 0 이면 소스 좌표에서 -spread/2 만큼 어긋난다', () => {
    const result = seedPosition({ x: 100, y: 200 }, 300, () => 0)
    expect(result).toEqual({ x: -50, y: 50 })
  })

  it('rand 가 1 이면 소스 좌표에서 +spread/2 만큼 어긋난다', () => {
    const result = seedPosition({ x: 100, y: 200 }, 300, () => 1)
    expect(result).toEqual({ x: 250, y: 350 })
  })

  it('rand 가 0.5 면 오프셋이 0 이라 소스 좌표와 같다', () => {
    const result = seedPosition({ x: 10, y: -20 }, 300, () => 0.5)
    expect(result).toEqual({ x: 10, y: -20 })
  })

  it('spread 를 생략하면 기본값 300 을 쓴다', () => {
    const result = seedPosition({ x: 0, y: 0 }, undefined, () => 0)
    expect(result).toEqual({ x: -150, y: -150 })
  })

  it('rand 를 생략하면 Math.random 을 써서 spread 절반 범위 안에 든다', () => {
    const result = seedPosition({ x: 0, y: 0 })
    expect(result.x).toBeGreaterThanOrEqual(-150)
    expect(result.x).toBeLessThanOrEqual(150)
    expect(result.y).toBeGreaterThanOrEqual(-150)
    expect(result.y).toBeLessThanOrEqual(150)
  })
})

describe('edgeId', () => {
  it('source-target 순서로 id 를 만든다', () => {
    expect(edgeId('1', '2')).toBe('1-2')
  })
})

describe('hasEdge', () => {
  it('정방향 id 가 집합에 있으면 true 를 반환한다', () => {
    const ids = new Set(['1-2'])
    expect(hasEdge(ids, '1', '2')).toBe(true)
  })

  it('역방향 id 만 있어도 true 를 반환한다', () => {
    const ids = new Set(['2-1'])
    expect(hasEdge(ids, '1', '2')).toBe(true)
  })

  it('양방향 다 없으면 false 를 반환한다', () => {
    const ids = new Set(['3-4'])
    expect(hasEdge(ids, '1', '2')).toBe(false)
  })
})
