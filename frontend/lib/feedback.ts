// 추천 피드백 전송, 익명 방문자 id 를 localStorage 에 두고 fire-and-forget 으로 보낸다
// 실패해도 UX 를 막지 않도록 예외를 밖으로 던지지 않는다

import { apiUrl } from './api'
import { getClientId } from './clientId'
import type { FeedbackContext, FeedbackSignal } from './types'

export function sgrFeedback(
  appid: number,
  signal: FeedbackSignal,
  context: FeedbackContext,
  rank?: number,
  steamId?: string,
): void {
  try {
    fetch(apiUrl('/api/recommend/feedback'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      keepalive: true,
      body: JSON.stringify({
        clientId: getClientId(),
        appid,
        signal,
        context,
        rankPosition: rank ?? null,
        steamId: (steamId && steamId.trim()) || null,
      }),
    }).catch(() => {})
  } catch {
    // 실패는 무시함
  }
}
