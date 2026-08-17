import { describe, expect, it } from 'vitest'
import { describeReason, describeCaveat } from '../explanations'

describe('describeReason', () => {
  it('IS_FREE 를 문구로 옮긴다', () => {
    expect(describeReason({ code: 'IS_FREE', params: {} })).toBe('Free to play')
  })

  it('WITHIN_BUDGET 는 budgetCents 를 달러로 녹인다', () => {
    expect(describeReason({ code: 'WITHIN_BUDGET', params: { priceCents: 1500, budgetCents: 2000 } }))
      .toBe('Within your $20 budget')
  })

  it('WELL_REVIEWED 는 pct 와 count 가 모두 있으면 둘 다 보여준다', () => {
    expect(describeReason({ code: 'WELL_REVIEWED', params: { positivePct: 92, reviewCount: 12000 } }))
      .toBe('92% positive from 12000 reviews')
  })

  it('WELL_REVIEWED 는 값이 하나도 없으면 짧은 문구로 낮춘다', () => {
    expect(describeReason({ code: 'WELL_REVIEWED', params: {} })).toBe('Well reviewed')
  })

  it('MOOD_MATCH 를 문구로 옮긴다', () => {
    expect(describeReason({ code: 'MOOD_MATCH', params: { rank: 0 } })).toBe('Matches your mood')
  })

  it('미지 코드는 null 을 반환한다', () => {
    expect(describeReason({ code: 'NOPE', params: {} })).toBeNull()
  })
})

describe('describeCaveat', () => {
  it('MOOD_UNAVAILABLE 를 문구로 옮긴다', () => {
    expect(describeCaveat({ code: 'MOOD_UNAVAILABLE', params: {} }))
      .toBe('Could not match your mood, showing top-quality picks instead')
  })

  it('LONGER_THAN_REQUESTED 는 typicalHours 를 반올림해 넣는다', () => {
    expect(describeCaveat({ code: 'LONGER_THAN_REQUESTED', params: { typicalHours: 42.6 } }))
      .toBe('Usually longer than you asked (about 43h)')
  })

  it('미지 코드는 null 을 반환한다', () => {
    expect(describeCaveat({ code: 'NOPE', params: {} })).toBeNull()
  })
})
