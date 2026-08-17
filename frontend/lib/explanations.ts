import type { ExplanationItem } from './types'

// typicalHours 같은 소수 파라미터를 정수로 반올림함, 값이 이상하면 0 으로 둔다
function roundHours(v: unknown): number {
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? Math.round(n) : 0
}

// 사유 코드 한 건을 영문 문구로 옮김, 미지 코드는 null 을 반환해 렌더에서 건너뛴다
export function describeReason(item: ExplanationItem): string | null {
  const p = item.params ?? {}
  switch (item.code) {
    case 'IS_FREE':
      return 'Free to play'
    case 'WITHIN_BUDGET': {
      const budgetCents = Number(p.budgetCents)
      if (!Number.isFinite(budgetCents)) return 'Within your budget'
      return `Within your $${Math.round(budgetCents / 100)} budget`
    }
    case 'COOP_SUPPORTED':
      return 'Co-op supported'
    case 'SOLO_FRIENDLY':
      return 'Great for solo play'
    case 'WELL_REVIEWED': {
      // positivePercentage 는 BigDecimal(소수 가능)이라 표시용으로 반올림함, typicalHours,예산과 일관성 맞춤
      const pct = p.positivePct != null ? Math.round(Number(p.positivePct)) : null
      const count = p.reviewCount != null ? Number(p.reviewCount) : null
      if (pct != null && count != null) return `${pct}% positive from ${count} reviews`
      if (pct != null) return `${pct}% positive`
      if (count != null) return `${count} reviews`
      return 'Well reviewed'
    }
    case 'VOLUME_FITS':
      return 'Fits your session length'
    case 'MOOD_MATCH':
      return 'Matches your mood'
    default:
      return null
  }
}

// 주의점 코드 한 건을 영문 문구로 옮김, 미지 코드는 null
export function describeCaveat(item: ExplanationItem): string | null {
  const p = item.params ?? {}
  switch (item.code) {
    case 'PLAYER_COUNT_APPROX':
      return 'Player-count fit is approximate'
    case 'PRICE_NO_DISCOUNT':
      return 'Price shown without any discount'
    case 'LONGER_THAN_REQUESTED':
      return `Usually longer than you asked (about ${roundHours(p.typicalHours)}h)`
    case 'SHORTER_THAN_REQUESTED':
      return `Usually shorter than you asked (about ${roundHours(p.typicalHours)}h)`
    case 'VOLUME_UNKNOWN':
      return 'Playtime data unavailable'
    case 'MOOD_UNAVAILABLE':
      return 'Could not match your mood, showing top-quality picks instead'
    case 'REVIEW_RELAXED':
      return 'Review bar relaxed to fill results'
    default:
      return null
  }
}
