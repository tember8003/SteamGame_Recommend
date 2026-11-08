import { describe, expect, it } from 'vitest';
import { libraryGenreLabel } from './format';

describe('libraryGenreLabel', () => {
  it('replaces the middle dot from genreName with a comma', () => {
    const label = libraryGenreLabel('ACTION_SHOOTER');
    expect(label).toBe('액션, 슈팅');
    expect(label).not.toContain('·');
  });

  it('shows INSUFFICIENT as a Korean phrase', () => {
    expect(libraryGenreLabel('INSUFFICIENT')).toBe('기록 부족');
  });
});
