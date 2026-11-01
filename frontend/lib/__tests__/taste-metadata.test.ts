import { afterEach, expect, it, vi } from 'vitest';
import { publicTasteMetadata } from '../taste/metadata';
afterEach(() => vi.unstubAllGlobals());
it('uses the API share copy and caches metadata reads', async () => {
  const fetcher = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ code: 'GMHS', share: { title: 'Server title', text: 'Server text' } }) });
  vi.stubGlobal('fetch', fetcher);
  expect((await publicTasteMetadata('GMHS', 'ko'))?.share.title).toBe('Server title');
  expect(fetcher.mock.calls[0][1].next).toEqual({ revalidate: 3600 });
});
it('allows a local metadata fallback on failure or mismatched codes', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
  expect(await publicTasteMetadata('GMHS', 'ko')).toBeNull();
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ code: 'ENCP', share: { title: 'Wrong type', text: 'wrong' } }) }));
  expect(await publicTasteMetadata('GMHS', 'ko')).toBeNull();
});
