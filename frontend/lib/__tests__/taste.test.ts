import { describe, expect, it } from 'vitest';
import { filterTasteTypes, getTasteType, tasteTypes } from '../taste/catalog';

describe('taste catalog', () => {
  it('provides all 16 unique combinations with actual character identities', () => {
    expect(tasteTypes).toHaveLength(16);
    expect(new Set(tasteTypes.map(t => t.code)).size).toBe(16);
    for (const e of ['E', 'G']) for (const n of ['N', 'M'])
      for (const h of ['H', 'C']) for (const s of ['S', 'P'])
        expect(getTasteType(`${e}${n}${h}${s}`)).toBeDefined();
    expect(getTasteType('ENCP')?.name.ko).toBe('조야');
    expect(getTasteType('ENHS')?.name.ko).toBe('기사');
  });
  it('handles case-insensitive lookup and rejects invalid codes', () => {
    expect(getTasteType('gmhs')?.name.en).toBe('Agent 47');
    expect(getTasteType('OTHER')).toBeUndefined();
  });
  it('combines localized search and axis filtering, including empty matches', () => {
    expect(filterTasteTypes('오버워치', '', 'ko')).toHaveLength(3);
    expect(filterTasteTypes('agent 47', 'S', 'en').map(t => t.code)).toEqual(['GMHS']);
    expect(filterTasteTypes('', 'P', 'ko')).toHaveLength(8);
    expect(filterTasteTypes('없는 이름', '', 'ko')).toEqual([]);
    expect(filterTasteTypes('   ', '', 'en')).toHaveLength(16);
  });
});
