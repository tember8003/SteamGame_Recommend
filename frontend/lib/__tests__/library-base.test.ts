import { describe, expect, it } from 'vitest';
import { baseTheme } from '../library/base';
import type { GenreProfile } from '../library/api';

const profile = (overrides: Partial<GenreProfile> = {}): GenreProfile => ({ primaryGenre: 'RPG_ADVENTURE', secondaryGenre: null, state: 'READY', coverageRatio: 0.8, distribution: [{ code: 'RPG_ADVENTURE', ratio: 1 }], ...overrides });
describe('library base presentation', () => {
  it('uses all seven dedicated backgrounds, including the actual RACING_SPORTS code', () => {
    const ids = ['RPG_ADVENTURE', 'STRATEGY_MANAGEMENT', 'ACTION_SHOOTER', 'SURVIVAL_CRAFTING', 'LIFE_FARMING', 'HORROR_MYSTERY', 'RACING_SPORTS'].map(primaryGenre => baseTheme(profile({ primaryGenre })).id);
    expect(new Set(ids).size).toBe(7);
  });
  it('uses puzzle and book props for valid non-dedicated genres without claiming missing data', () => {
    for (const primaryGenre of ['PUZZLE', 'VISUAL_NOVEL']) {
      const theme = baseTheme(profile({ primaryGenre }));
      expect(theme.id).toBe('default'); expect(theme.prop).not.toBeNull(); expect(theme.insufficient).toBe(false);
    }
  });
  it('distinguishes old shared snapshots from insufficient analysis', () => {
    expect(baseTheme(null).insufficient).toBe(false);
    expect(baseTheme(profile({ state: 'INSUFFICIENT', primaryGenre: null })).insufficient).toBe(true);
  });
  it('honors server state rather than recomputing rounded thresholds and handles mixed without a secondary', () => {
    expect(baseTheme(profile({ state: 'INSUFFICIENT', coverageRatio: 0.3 })).id).toBe('default');
    const theme = baseTheme(profile({ state: 'MIXED', secondaryGenre: null }));
    expect(theme.id).toBe('rpg'); expect(theme.mixed).toBe(true); expect(theme.secondary).toBeNull();
    expect(baseTheme(profile({ primaryGenre: 'FUTURE_GENRE' })).id).toBe('default');
  });
});
