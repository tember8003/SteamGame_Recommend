import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, tasteRequest, fireAndForget } from '../api';
import { optionalClientId, recordLandingVisit, tasteQuestions, tasteResult, tasteRecommend } from '../taste/api';

afterEach(() => vi.unstubAllGlobals());
describe('taste API contract', () => {
  it('preserves the version conflict message and ignores malformed error bodies', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 409, json: async () => ({ message: 'TASTE_VERSION_MISMATCH' }) }));
    await expect(tasteRequest('/test')).rejects.toMatchObject({ status: 409, code: 'TASTE_VERSION_MISMATCH' });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 502, json: async () => { throw new Error(); } }));
    await expect(tasteRequest('/test')).rejects.toBeInstanceOf(ApiError);
  });
  it('reads rate limit delay from JSON rather than inaccessible headers', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 429, json: async () => ({ retryAfterSeconds: 12 }) }));
    await expect(tasteRequest('/test')).rejects.toMatchObject({ code: 'RATE_LIMITED', retryAfterSeconds: 12 });
  });
  it('sends JSON events without parsing their empty responses and deduplicates landing visits', () => {
    const json = vi.fn();
    const fetcher = vi.fn().mockResolvedValue({ ok: true, status: 202, json });
    vi.stubGlobal('fetch', fetcher);
    sessionStorage.clear();
    recordLandingVisit(); recordLandingVisit();
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(json).not.toHaveBeenCalled();
    fireAndForget('/event', { type: 'TEST_START' });
    expect(fetcher.mock.calls.at(-1)?.[1]).toMatchObject({ headers: { 'Content-Type': 'application/json' }, keepalive: true });
  });
  it('can submit answers even when localStorage is unavailable', async () => {
    vi.stubGlobal('localStorage', { getItem: () => { throw new Error('Denied'); } });
    expect(optionalClientId()).toBeUndefined();
    const fetcher = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ code: 'GMHS' }) });
    vi.stubGlobal('fetch', fetcher);
    await tasteResult('v1', Array(12).fill('A'), 'ko');
    const init = fetcher.mock.calls[0][1];
    expect(JSON.parse(init.body)).toEqual({ version: 'v1', answers: Array(12).fill('A') });
    expect(init.headers['Content-Type']).toBe('application/json');
  });
  it('limits recommendation exclusions to the most recent 500 games', async () => {
    const fetcher = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ games: [], explanations: [] }) });
    vi.stubGlobal('fetch', fetcher);
    await tasteRecommend('GMHS', Array.from({ length: 502 }, (_, index) => index));
    const payload = JSON.parse(fetcher.mock.calls[0][1].body);
    expect(payload.excludeAppids).toHaveLength(500);
    expect(payload.excludeAppids[0]).toBe(2);
    expect(payload.excludeAppids.at(-1)).toBe(501);
  });
  it('explicitly requests the v2 questions', async () => {
    const fetcher = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ version: 'v2', questions: [] }) });
    vi.stubGlobal('fetch', fetcher);
    await tasteQuestions('en');
    expect(fetcher.mock.calls[0][0]).toContain('/api/taste/questions?lang=en&version=v2');
  });

});
