import type { ExplanationItem } from './types'
import type { TFunction } from './i18n/useT'

// 사유 코드 한 건을 현재 로케일 문구로 옮김, 미지 코드는 null 을 반환해 렌더에서 건너뛴다
export function describeReason(item: ExplanationItem, t: TFunction): string | null {
  const p = item.params ?? {}
  switch (item.code) {
    case 'TASTE_TAG_MATCH': {
      const tags = Array.isArray(p.tags) ? p.tags.filter((tag): tag is string => typeof tag === 'string' && tag.trim().length > 0).slice(0, 3) : []
      return tags.length ? t('explain.reason.tasteTagMatch', { tags: tags.join(', ') }) : null
    }
    case 'BECAUSE_YOU_LIKED': {
      // 좋아요 게임 이름이 실려 올 때만 지명 사유를 냄, 비었으면 렌더에서 건너뛴다
      const game = typeof p.gameName === 'string' ? p.gameName.trim() : ''
      if (!game) return null
      return t('explain.reason.becauseYouLiked', { game })
    }
    case 'IS_FREE':
      return t('explain.reason.isFree')
    case 'WITHIN_BUDGET': {
      const budgetCents = Number(p.budgetCents)
      if (!Number.isFinite(budgetCents)) return t('explain.reason.withinBudgetGeneric')
      return t('explain.reason.withinBudget', { budget: Math.round(budgetCents / 100) })
    }
    case 'COOP_SUPPORTED':
      return t('explain.reason.coopSupported')
    case 'SOLO_FRIENDLY':
      return t('explain.reason.soloFriendly')
    case 'WELL_REVIEWED': {
      // positivePercentage 는 소수 가능이라 표시용으로 반올림함
      const pct = p.positivePct != null ? Math.round(Number(p.positivePct)) : null
      const count = p.reviewCount != null ? Number(p.reviewCount) : null
      if (pct != null && count != null) return t('explain.reason.wellReviewed.both', { pct, count })
      if (pct != null) return t('explain.reason.wellReviewed.pct', { pct })
      if (count != null) return t('explain.reason.wellReviewed.count', { count })
      return t('explain.reason.wellReviewed.plain')
    }
    case 'MOOD_MATCH':
      return t('explain.reason.moodMatch')
    default:
      return null
  }
}

// 주의점 코드 한 건을 현재 로케일 문구로 옮김, 미지 코드는 null
export function describeCaveat(item: ExplanationItem, t: TFunction): string | null {
  switch (item.code) {
    case 'PLAYER_COUNT_APPROX':
      return t('explain.caveat.playerCountApprox')
    case 'PRICE_NO_DISCOUNT':
      return t('explain.caveat.priceNoDiscount')
    case 'MOOD_UNAVAILABLE':
      return t('explain.caveat.moodUnavailable')
    case 'REVIEW_RELAXED':
      return t('explain.caveat.reviewRelaxed')
    default:
      return null
  }
}
