import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import LibraryBase from '../library/LibraryBase';
import fixture from './fixtures/library-contract.json';
import type { LibrarySnapshot } from '@/lib/library/api';
import { libraryImageSections, libraryImageSize } from '@/lib/library/export';

afterEach(cleanup);
const picks = {
  playNext: [
    { appid: 294100, name: 'RimWorld', matchedTags: ['Colony Sim', 'Strategy'] },
    { appid: 620, name: 'Portal 2', matchedTags: ['Puzzle'] },
  ],
  revisit: [{ appid: 105600, name: 'Terraria', hours: 320.5, idleDays: 540 }],
  abandonedGenre: { genre: 'HORROR_MYSTERY', gameCount: 4, games: ['Night One', 'Night Two'] },
};
const snapshot = { ...fixture.shared, picks } as LibrarySnapshot;

describe('library picks in their places', () => {
  it('puts untouched server picks before warehouse statistics and preserves tag order', () => {
    render(<LibraryBase data={snapshot} locale="ko" />);
    const region = screen.getByRole('region', { name: '창고에서 꺼내볼 게임' });
    expect(within(region).getAllByRole('link').map(link => link.textContent)).toEqual([
      expect.stringContaining('RimWorld'), expect.stringContaining('Portal 2'),
    ]);
    expect(region).toHaveTextContent('Colony Sim, Strategy');
    expect(region.compareDocumentPosition(screen.getByText('창고에 모인 게임')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(within(region).getAllByRole('link')[0]).toHaveAttribute('href', 'https://store.steampowered.com/app/294100/?l=korean');
  });
  it('places revisits above memories and uses snapshot-relative dates in both languages', () => {
    const { rerender } = render(<LibraryBase data={snapshot} locale="ko" />);
    fireEvent.click(screen.getByRole('tab', { name: /추억의 전시장/ }));
    expect(screen.getByRole('region', { name: '다시 이어갈 모험' })).toHaveTextContent('320.5 시간');
    expect(screen.getByText('분석 당시 마지막 플레이 540일 전')).toBeVisible();
    expect(screen.getByText('가장 오래 머문 세 세계')).toBeVisible();
    rerender(<LibraryBase data={snapshot} locale="en" />);
    expect(screen.getByText('Last played 540 days before analysis')).toBeVisible();
  });
  it('shows a nonjudgmental genre record with a safe unknown-genre fallback', () => {
    const data = { ...snapshot, picks: { ...picks, abandonedGenre: { ...picks.abandonedGenre, genre: 'FUTURE_GENRE' } } };
    render(<LibraryBase data={data} locale="ko" />);
    fireEvent.click(screen.getByRole('tab', { name: /다음 모험 지도/ }));
    const region = screen.getByRole('region', { name: '잠시 멀어진 장르' });
    expect(region).toHaveTextContent('기타 장르');
    expect(region).toHaveTextContent('4개');
    expect(region).toHaveTextContent('Night One');
    expect(region).not.toHaveTextContent('FUTURE_GENRE');
  });
  it.each([null, undefined, { playNext: [], revisit: [], abandonedGenre: null }])('hides all empty or legacy picks without removing existing content: %j', picks => {
    render(<LibraryBase data={{ ...snapshot, picks }} locale="ko" />);
    expect(screen.queryByRole('region', { name: '창고에서 꺼내볼 게임' })).not.toBeInTheDocument();
    expect(screen.getByText('창고에 모인 게임')).toBeVisible();
    fireEvent.click(screen.getByRole('tab', { name: /추억의 전시장/ }));
    expect(screen.queryByRole('region', { name: '다시 이어갈 모험' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: /다음 모험 지도/ }));
    expect(screen.queryByRole('region', { name: '잠시 멀어진 장르' })).not.toBeInTheDocument();
  });
  it('independently hides an empty playNext while still showing revisit', () => {
    render(<LibraryBase data={{ ...snapshot, picks: { ...picks, playNext: [] } }} locale="ko" />);
    expect(screen.queryByRole('region', { name: '창고에서 꺼내볼 게임' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: /추억의 전시장/ }));
    expect(screen.getByRole('region', { name: '다시 이어갈 모험' })).toBeVisible();
  });
  it('exports picks in the matching places with more room and no identity', () => {
    const sections = libraryImageSections(snapshot, 'ko');
    expect(JSON.stringify(sections[0])).toContain('Colony Sim, Strategy');
    expect(JSON.stringify(sections[1])).toContain('540일 전');
    expect(JSON.stringify(sections[3])).toContain('잠시 멀어진 장르');
    expect(JSON.stringify(sections)).not.toContain('steamId64');
    expect(libraryImageSize(snapshot, 'ko').height).toBeGreaterThan(libraryImageSize(fixture.shared as LibrarySnapshot, 'ko').height);
  });
});
