import { afterEach, describe, expect, it, vi } from 'vitest';
import { libraryAnalyze, libraryShare, libraryShared } from '../library/api';
import { fillLibraryText } from '../library/presentation';

vi.mock('../clientId', () => ({ getClientId: () => 'test-library-client' }));
afterEach(() => vi.unstubAllGlobals());

describe('library contract', () => {
  it('sends vanity inputs unchanged with clientId and locale, without caching', async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify({ analysisId: 'analysis' })));
    vi.stubGlobal('fetch', fetcher);
    await libraryAnalyze('  https://steamcommunity.com/id/example  ', 'ko');
    const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toContain('/api/library/analyze?lang=ko');
    expect(JSON.parse(init.body as string)).toEqual({ steamId: 'https://steamcommunity.com/id/example', clientId: 'test-library-client' });
    expect(init.cache).toBe('no-store');
    expect(init.signal).toBeInstanceOf(AbortSignal);
  });
  it('creates a share using only analysisId and reads it in the selected language', async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify({ shareId: 'abcdefghijklmnopqrstuv' })));
    vi.stubGlobal('fetch', fetcher);
    await libraryShare('analysis');
    expect(JSON.parse((fetcher.mock.calls[0] as unknown as [string, RequestInit])[1].body as string)).toEqual({ analysisId: 'analysis' });
    await libraryShared('abcdefghijklmnopqrstuv', 'en');
    expect((fetcher.mock.calls[1] as unknown as [string])[0]).toContain('/api/library/share/abcdefghijklmnopqrstuv?lang=en');
  });
  it('fills parameters once and keeps inserted game names as plain text', () => {
    expect(fillLibraryText({ text: '{topGame}: {totalHours}h', params: { topGame: '<b>{totalHours}</b>', totalHours: 42 }, code: 'UNKNOWN' })).toBe('<b>{totalHours}</b>: 42h');
  });
});
