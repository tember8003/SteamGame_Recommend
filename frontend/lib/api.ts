// 백엔드 API 클라이언트, 브라우저가 NEXT_PUBLIC_API_BASE 로 백엔드를 직접 호출한다
// 프록시나 Next route handler 를 거치지 않는다

import type {
  ChoiceResult,
  ChoiceSelectionResponse,
  ChoiceStartResponse,
  GameSearchResult,
  GraphNode,
  MatchOptions,
  MatchResult,
  NeighborsResponse,
  RecommendationResult,
  RecommendOptions,
  RecommendRandomOptions,
  ReviewGuessAnswerResponse,
  ReviewGuessResult,
  ReviewGuessStartResponse,
} from './types'
import { getClientId } from './clientId'

// NEXT_PUBLIC_API_BASE 와 경로를 합쳐 완전한 요청 URL 을 만든다, feedback.ts 도 이 헬퍼를 재사용함
export function apiUrl(path: string): string {
  const base = process.env.NEXT_PUBLIC_API_BASE ?? ''
  return `${base}${path}`
}

async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(apiUrl(path))
  if (!res.ok) throw new Error(`GET ${path} failed: ${res.status}`)
  return res.json() as Promise<T>
}

async function postJson<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(apiUrl(path), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`POST ${path} failed: ${res.status}`)
  return res.json() as Promise<T>
}

// recommend/* 요청 바디 중 review, korean_check, free_check, excludeAppids 공통 부분을 조립함
function recommendCommonBody(options: RecommendOptions) {
  return {
    review: options.review,
    korean_check: options.koreanCheck ?? null,
    free_check: options.freeCheck ?? null,
    excludeAppids: options.excludeAppids,
    clientId: getClientId(),
  }
}

// ── graph ──

export const graphSearch = (q: string) => getJson<GraphNode[]>(`/api/graph/search?q=${encodeURIComponent(q)}`)

export const graphNeighbors = (appid: number, k?: number) =>
  getJson<NeighborsResponse>(`/api/graph/neighbors?appid=${appid}${k !== undefined ? `&k=${k}` : ''}`)

// ── game ──

export const gamesSearch = (query: string) =>
  getJson<GameSearchResult[]>(`/api/games/search?query=${encodeURIComponent(query)}`)

// ── recommend ──

export const recommendInput = (text: string, options: RecommendOptions) =>
  postJson<RecommendationResult>('/api/recommend/input', { text, ...recommendCommonBody(options) })

export const recommendProfile = (steamId: string, options: RecommendOptions) =>
  postJson<RecommendationResult>('/api/recommend/profile', { steamId, ...recommendCommonBody(options) })

export const recommendRecent = (steamId: string, options: RecommendOptions) =>
  postJson<RecommendationResult>('/api/recommend/recent-play', { steamId, ...recommendCommonBody(options) })

export const recommendSimilar = (gameName: string, options: RecommendOptions) =>
  postJson<RecommendationResult>('/api/recommend/similar', { gameName, ...recommendCommonBody(options) })

export const recommendRandom = (tags: string[], options: RecommendRandomOptions) =>
  postJson<RecommendationResult>('/api/recommend/random', {
    tags,
    ...recommendCommonBody(options),
    excludedTag: options.excludedTag ?? null,
  })

// /match 는 필드명이 MatchRequestDTO 와 달라 recommendCommonBody 를 쓰지 않고 자체 바디를 만든다
export const recommendMatch = (options: MatchOptions) =>
  postJson<MatchResult>('/api/recommend/match', {
    rawText: options.rawText,
    sessionLength: options.sessionLength ?? null,
    budgetUsd: options.budgetUsd ?? null,
    players: options.players ?? null,
    review: options.review,
    korean: options.korean ?? null,
    free: options.free ?? null,
    excludeAppids: options.excludeAppids,
    clientId: getClientId(),
  })

// ── choice ──

export const choiceStart = () => postJson<ChoiceStartResponse>('/api/choice/start', {})

export const choiceSelect = (sessionId: string, roundIndex: number, selectedGameId: number) =>
  postJson<ChoiceSelectionResponse>('/api/choice/select', { sessionId, roundIndex, selectedGameId })

export const choiceResult = (
  sessionId: string,
  review: number,
  koreanCheck?: boolean | null,
  freeCheck?: boolean | null,
) =>
  postJson<ChoiceResult>('/api/choice/result', {
    sessionId,
    review,
    korean_check: koreanCheck ?? null,
    free_check: freeCheck ?? null,
  })

// ── review guess ──

export const reviewGuessStart = () => postJson<ReviewGuessStartResponse>('/api/review-guess/start', {})

export const reviewGuessAnswer = (sessionId: string, roundIndex: number, selectedGameId: number) =>
  postJson<ReviewGuessAnswerResponse>('/api/review-guess/answer', { sessionId, roundIndex, selectedGameId })

export const reviewGuessResult = (sessionId: string) =>
  getJson<ReviewGuessResult>(`/api/review-guess/${encodeURIComponent(sessionId)}/result`)
