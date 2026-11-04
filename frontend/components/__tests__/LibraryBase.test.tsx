import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import fixture from './fixtures/library-contract.json';
import LibraryBase from '../library/LibraryBase';
import type { GenreProfile, LibrarySnapshot } from '@/lib/library/api';

afterEach(cleanup);
const profile: GenreProfile = { primaryGenre: 'RACING_SPORTS', secondaryGenre: null, state: 'READY', coverageRatio: .8, distribution: [{ code: 'RACING_SPORTS', ratio: 1 }] };
const data = (genreProfile: GenreProfile | null) => ({ ...fixture.shared, genreProfile }) as LibrarySnapshot;

describe('interactive genre base', () => {
  it('renders the racing garage and navigates all places by keyboard without court copy', () => {
    render(<LibraryBase data={data(profile)} locale="ko" />);
    expect(screen.getByRole('heading', { name: '트로피 차고' })).toBeInTheDocument();
    expect(document.querySelector('img')?.getAttribute('src')).toBe('/images/library/racing.webp');
    expect(screen.queryByText(fixture.shared.verdict.crime.text)).not.toBeInTheDocument();
    const warehouse = screen.getByRole('tab', { name: /수집품 창고/ });
    fireEvent.keyDown(warehouse, { key: 'ArrowRight' });
    expect(screen.getByRole('tab', { name: /추억의 전시장/ })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tabpanel')).toHaveTextContent('The Witcher 3');
    fireEvent.click(screen.getByRole('tab', { name: /캐릭터 상태창/ }));
    expect(screen.getAllByRole('meter')).toHaveLength(5);
    fireEvent.click(screen.getByRole('tab', { name: /다음 모험 지도/ }));
    expect(screen.getByRole('tabpanel')).toHaveTextContent('레이싱·스포츠');
    expect(screen.getByRole('tabpanel')).toHaveTextContent('80%');
  });
  it('handles mixed without a secondary and never invents one from distribution', () => {
    render(<LibraryBase data={data({ ...profile, state: 'MIXED', distribution: [{ code: 'RACING_SPORTS', ratio: .11 }, { code: 'PUZZLE', ratio: .11 }] })} locale="ko" />);
    expect(screen.getByText('여러 세계를 오가는 여행자')).toBeInTheDocument();
    expect(screen.queryByText(/이런 취향도 함께/)).not.toBeInTheDocument();
  });
  it('treats old snapshots and valid puzzle profiles differently from insufficient data', () => {
    const { rerender } = render(<LibraryBase data={data(null)} locale="ko" />);
    expect(screen.getByRole('heading', { name: '여행자의 거점' })).toBeInTheDocument();
    expect(screen.queryByText(/장르를 읽을 기록이 아직 적어/)).not.toBeInTheDocument();
    rerender(<LibraryBase data={data({ ...profile, primaryGenre: 'PUZZLE' })} locale="ko" />);
    expect(screen.getByLabelText('퍼즐 도구')).toBeInTheDocument();
    expect(screen.queryByText(/장르를 읽을 기록이 아직 적어/)).not.toBeInTheDocument();
    rerender(<LibraryBase data={data({ ...profile, primaryGenre: null, state: 'INSUFFICIENT' })} locale="ko" />);
    expect(screen.getByText(/장르를 읽을 기록이 아직 적어/)).toBeInTheDocument();
  });
  it('shows a genre companion rather than claiming a scored test type', () => {
    render(<LibraryBase data={data({ ...profile, primaryGenre: 'LIFE_FARMING' })} locale="ko" />);
    fireEvent.click(screen.getByRole('tab', { name: /캐릭터 상태창/ }));
    expect(screen.getByRole('img', { name: '로빈' })).toBeInTheDocument();
    expect(screen.getByText('취향의 동행자')).toBeVisible();
    expect(screen.getByText(/테스트 유형 판정은 아니에요/)).toBeVisible();
    expect(screen.getAllByRole('meter')).toHaveLength(5);
  });
  it('uses a traveler for insufficient genre data', () => {
    render(<LibraryBase data={data({ ...profile, state: 'INSUFFICIENT' })} locale="en" />);
    fireEvent.click(screen.getByRole('tab', { name: /Character attributes/ }));
    expect(screen.getByText('A fellow traveler')).toBeVisible();
  });

});
