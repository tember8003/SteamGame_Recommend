import { ApiError, fireAndForget, tasteRequest } from '../api';
import { getClientId } from '../clientId';
import type { Locale } from '../i18n/locales';
import type { ExplanationItem, SteamApp } from '../types';
import { getTasteType, type TasteType } from './catalog';

export type Answer = 'A' | 'B' | 'C' | 'D';
export interface TasteQuestion { id: number; text: string; options: { key: Answer; text: string }[] }
export interface TasteQuestions { version: string; questions: TasteQuestion[] }
export interface TypeSummary { code: string; name: string; work: string; oneLiner: string }
export interface TypeView extends TypeSummary {
  workAppid: number; description: string; traits: string;
  axes: { id: string; letter: string; label: string }[];
  tags: { tag: string; weight: number }[]; featuredGames: SteamApp[];
  share: { title: string; text: string };
}
export interface TasteAxisDetail {
  id: string; left: string; right: string; total: number;
  leftPercent: number; rightPercent: number; winner: string;
  balance: 'BALANCED' | 'LEANING' | 'STRONG'; tieBroken: boolean; summary: string;
}
export interface TasteResult { version?: 'v2'; axisDetails?: TasteAxisDetail[]; code: string; axisScores: Record<string, Record<string, number>>; type: TypeView }
export interface TasteRecommendations { games: SteamApp[]; explanations: { appid: number; items: ExplanationItem[] }[] }
type EventType = 'LANDING_VISIT' | 'FEATURE_SELECT' | 'TEST_START' | 'SHARE_CLICK' | 'SHARE_INFLOW';

export function optionalClientId(): string | undefined {
  try { const id = getClientId(); return /^[A-Za-z0-9_-]{1,64}$/.test(id) ? id : undefined } catch { return undefined }
}
export function recordTasteEvent(type: EventType, ref?: string, channel?: 'LINK' | 'IMAGE') {
  fireAndForget('/api/events', { type, ref, channel, clientId: optionalClientId() });
}
let landingInMemory = false;
export function recordLandingVisit() {
  try {
    if (sessionStorage.getItem('playalike_landing_visit')) return;
    sessionStorage.setItem('playalike_landing_visit', '1');
  } catch { if (landingInMemory) return; landingInMemory = true }
  recordTasteEvent('LANDING_VISIT');
}
const post = <T>(path: string, body: unknown) => tasteRequest<T>(path, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
});
export const tasteQuestions = (locale: Locale, reload = false, signal?: AbortSignal) =>
  tasteRequest<TasteQuestions>(`/api/taste/questions?lang=${locale}&version=v2`, { ...(reload ? { cache: 'reload' as const } : {}), signal });
export const tasteSummaries = (locale: Locale, signal?: AbortSignal) => tasteRequest<TypeSummary[]>(`/api/taste/types?lang=${locale}`, { signal });
export const tasteType = (code: string, locale: Locale, signal?: AbortSignal) => tasteRequest<TypeView>(`/api/taste/types/${encodeURIComponent(code)}?lang=${locale}`, { signal });
export const tasteResult = (version: string, answers: Answer[], locale: Locale) =>
  post<TasteResult>(`/api/taste/result?lang=${locale}`, { version, answers, clientId: optionalClientId() });
export const tasteRecommend = (code: string, excludeAppids: number[]) => post<TasteRecommendations>('/api/taste/recommend', {
  code, review: 300, korean: null, free: null, excludeAppids: excludeAppids.slice(-500), clientId: optionalClientId(),
});

// 그림과 탐색 순서는 기존 도감에서 가져오고 표시 문구는 현재 API 응답으로 갱신한다.
export function catalogFromApi(view: TypeSummary | TypeView, locale: Locale): TasteType {
  const original = getTasteType(view.code);
  if (!original) throw new Error('Unknown taste code');
  const localized = (value: { ko: string; en: string }, text: string) => ({ ...value, [locale]: text });
  return { ...original, name: localized(original.name, view.name), work: localized(original.work, view.work), oneLiner: localized(original.oneLiner, view.oneLiner),
    ...('description' in view ? { workAppid: view.workAppid, description: localized(original.description, view.description), traits: localized(original.traits, view.traits), tagWeights: view.tags,
      share: { title: localized(original.share.title, view.share.title), text: localized(original.share.text, view.share.text) } } : {}),
  };
}
export function tasteErrorText(error: unknown, locale: Locale): string {
  const ko = locale === 'ko';
  if (error instanceof ApiError && error.status === 429) return ko ? `요청이 많아요. ${error.retryAfterSeconds ?? 30}초 뒤 다시 시도해주세요.` : `Too many requests. Try again in ${error.retryAfterSeconds ?? 30} seconds.`;
  if (error instanceof ApiError && error.status === 400) return ko ? '요청을 처리하지 못했어요. 답변을 확인하고 다시 시도해주세요.' : 'The request could not be processed. Check your answers and try again.';
  if (error instanceof ApiError && error.code === 'TASTE_TYPE_NOT_FOUND') return ko ? '이 유형을 찾을 수 없어요. 도감에서 다른 유형을 선택해주세요.' : 'This type is unavailable. Choose another type from the atlas.';
  return ko ? '서버에 연결하지 못했어요. 잠시 후 다시 시도해주세요.' : 'Could not reach the server. Please try again.';
}
