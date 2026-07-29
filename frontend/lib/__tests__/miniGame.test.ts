import { describe, expect, it } from 'vitest'
import { roundProgress } from '../miniGame'

describe('roundProgress', () => {
  it('첫 라운드면 전체 라운드 수 대비 1라운드만큼 퍼센트를 계산한다', () => {
    const result = roundProgress(0, 7)
    expect(result.percent).toBeCloseTo(14.2857, 4)
    expect(result.label).toBe('Round 1 of 7')
  })

  it('마지막 라운드면 퍼센트가 100 이 된다', () => {
    expect(roundProgress(6, 7)).toEqual({ percent: 100, label: 'Round 7 of 7' })
  })

  it('전체 라운드 수가 0 이하면 퍼센트는 0, 라벨은 라운드 번호만 남는다', () => {
    expect(roundProgress(2, 0)).toEqual({ percent: 0, label: 'Round 3' })
  })
})
