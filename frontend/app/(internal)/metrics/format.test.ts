import { describe, expect, it } from 'vitest';
import { formatKst, formatKstShort, libraryGenreLabel } from './format';

describe('libraryGenreLabel', () => {
  it('replaces the middle dot from genreName with a comma', () => {
    const label = libraryGenreLabel('ACTION_SHOOTER');
    expect(label).toBe('액션, 슈팅');
    expect(label).not.toContain('\u00B7');
  });

  it('shows INSUFFICIENT as a Korean phrase', () => {
    expect(libraryGenreLabel('INSUFFICIENT')).toBe('기록 부족');
  });
});

// 테스트 실행 시간대는 vitest.config.ts 에서 UTC 로 고정한다, 그래야 timeZone 옵션을 빼먹어도
// 우연히 통과하는(이 기기의 Node 기본 시간대가 Asia/Seoul 이라) 상황이 생기지 않는다
describe('formatKst / formatKstShort', () => {
  it('converts a UTC instant to KST', () => {
    expect(formatKstShort('2026-11-08T05:47:22Z')).toBe('11-08 14:47');
    expect(formatKst('2026-11-08T05:47:22Z')).toBe('2026-11-08 14:47');
  });

  it('crosses midnight into the next day at the KST boundary', () => {
    expect(formatKstShort('2026-11-08T15:00:00Z')).toBe('11-09 00:00');
  });

  it('stays on the same day just before the KST boundary', () => {
    expect(formatKstShort('2026-11-08T14:59:00Z')).toBe('11-08 23:59');
  });

  it("shows '-' for an invalid date", () => {
    expect(formatKst('not-a-date')).toBe('-');
    expect(formatKstShort('not-a-date')).toBe('-');
  });
});
