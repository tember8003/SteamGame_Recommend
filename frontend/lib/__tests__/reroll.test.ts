import { describe, expect, it } from 'vitest'
import { mergeShownAppids, toExcludeAppids } from '../reroll'

describe('mergeShownAppids', () => {
  it('이전 목록이 비어있으면 이번 게임들의 appid 를 그대로 담는다', () => {
    const result = mergeShownAppids([], [{ appid: 1 }, { appid: 2 }])
    expect(result).toEqual([1, 2])
  })

  it('이전 목록에 새 appid 를 이어붙인다', () => {
    const result = mergeShownAppids([1, 2], [{ appid: 3 }])
    expect(result).toEqual([1, 2, 3])
  })

  it('이미 있던 appid 는 중복으로 더하지 않는다', () => {
    const result = mergeShownAppids([1, 2], [{ appid: 2 }, { appid: 3 }])
    expect(result).toEqual([1, 2, 3])
  })

  it('같은 응답(배치) 안에 같은 appid 가 중복으로 들어와도 한 번만 담는다', () => {
    const result = mergeShownAppids([], [{ appid: 5 }, { appid: 5 }, { appid: 6 }])
    expect(result).toEqual([5, 6])
  })
})

describe('toExcludeAppids', () => {
  it('누적 목록이 비어있으면 undefined 를 반환한다', () => {
    expect(toExcludeAppids([])).toBeUndefined()
  })

  it('누적 목록이 있으면 그대로 반환한다', () => {
    expect(toExcludeAppids([1, 2, 3])).toEqual([1, 2, 3])
  })
})
