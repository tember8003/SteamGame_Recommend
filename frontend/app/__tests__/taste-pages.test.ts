import { describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { getTasteType, tasteTypes } from '@/lib/taste/catalog';
import TypePage, { generateMetadata, generateStaticParams } from '../[locale]/taste/[code]/page';
import { generateMetadata as atlasMetadata } from '../[locale]/taste/page';

vi.mock('next/navigation', () => ({ notFound: () => { throw new Error('NEXT_NOT_FOUND'); } }));

describe('taste pages', () => {
  it('prebuilds every type and has a real PNG for each character', () => {
    expect(generateStaticParams()).toHaveLength(16);
    for (const type of tasteTypes) {
      const png = readFileSync(path.join(process.cwd(), 'public/images/taste', `${type.code}.png`));
      expect(png.subarray(0, 8).toString('hex')).toBe('89504e470d0a1a0a');
      expect(png.length).toBeGreaterThan(1000);
    }
  });
  it('returns localized, canonical metadata for sharing', async () => {
    const meta = await generateMetadata({ params: Promise.resolve({ locale: 'en', code: 'encp' }) });
    expect(meta.title).toBe('Zoya / ENCP | Playalike');
    expect(meta.alternates?.canonical).toBe('https://steam-game-recommend.vercel.app/en/taste/ENCP');
    expect(meta.description).toBe(getTasteType('ENCP')!.oneLiner.en);
  });
  it('gives the atlas its own social preview rather than inheriting home metadata', async () => {
    const meta = await atlasMetadata({ params: Promise.resolve({ locale: 'en' }) });
    expect(meta.openGraph).toMatchObject({ url: 'https://steam-game-recommend.vercel.app/en/taste', title: '16 player characters | Playalike' });
    expect(meta.twitter).toMatchObject({ title: '16 player characters | Playalike' });
  });
  it('rejects invalid codes and locales rather than displaying the wrong character', async () => {
    await expect(TypePage({ params: Promise.resolve({ locale: 'ko', code: 'XXXX' }) })).rejects.toThrow('NEXT_NOT_FOUND');
    await expect(TypePage({ params: Promise.resolve({ locale: 'ja', code: 'GMHS' }) })).rejects.toThrow('NEXT_NOT_FOUND');
  });
});
