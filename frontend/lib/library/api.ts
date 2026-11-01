import { tasteRequest } from '../api';
import { getClientId } from '../clientId';
import type { Locale } from '../i18n/locales';

export interface LibraryText { code: string; text: string; params: Record<string, string | number> }
export interface PlayedGame { appid: number; name: string; hours: number; ratio: number }
export interface GenreProfile {
  primaryGenre: string | null; secondaryGenre: string | null;
  state: 'READY' | 'MIXED' | 'INSUFFICIENT'; coverageRatio: number;
  distribution: { code: string; ratio: number }[];
}
export interface LibrarySnapshot {
  genreProfile?: GenreProfile | null;
  limitedData: boolean;
  stats: {
    ownedCount: number; totalHours: number; zeroPlayCount: number; zeroPlayRatio: number;
    mostPlayed: PlayedGame | null; recentGameCount: number; recentHours: number; matchedCount: number;
    topTags: { tag: string; hours: number; ratio: number }[];
  };
  ledger: LibraryText;
  verdict: { crime: LibraryText; sentence: LibraryText };
  gamerStats: { code: string; value: number | null; label: string }[];
  diagnosis: { favoriteTag: string | null; hiddenTaste: string | null; concentratedGames: PlayedGame[] };
}
export interface LibraryAnalysis extends LibrarySnapshot {
  analysisId: string;
  profile: { personaName: string; avatarUrl: string; steamId64: string };
}

let memoryClientId: string | undefined;
function clientId(): string {
  try { const id = getClientId(); if (/^[A-Za-z0-9_-]{1,64}$/.test(id)) return id; } catch {}
  return memoryClientId ??= crypto.randomUUID();
}

export const libraryAnalyze = (steamId: string, locale: Locale) => tasteRequest<LibraryAnalysis>(`/api/library/analyze?lang=${locale}`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ steamId: steamId.trim(), clientId: clientId() }),
  cache: 'no-store', signal: AbortSignal.timeout(45000),
});
export const libraryShare = (analysisId: string) => tasteRequest<{ shareId: string }>('/api/library/share', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ analysisId }),
  cache: 'no-store', signal: AbortSignal.timeout(15000),
});
export const libraryShared = (shareId: string, locale: Locale, signal?: AbortSignal) =>
  tasteRequest<LibrarySnapshot>(`/api/library/share/${encodeURIComponent(shareId)}?lang=${locale}`, { cache: 'no-store', signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(15000)]) : AbortSignal.timeout(15000) });
