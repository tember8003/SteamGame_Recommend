import "@testing-library/jest-dom/vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import MetricsPage from './page';
import { fetchDashboard, DashboardAuthError } from './api';
import type { Dashboard } from './types';

vi.mock('./api', () => ({
  fetchDashboard: vi.fn(),
  DashboardAuthError: class extends Error {},
}));

const TOKEN_KEY = 'playloom_metrics_token';

const sample: Dashboard = {
  range: '7d', from: '2026-11-02', to: '2026-11-08', timezone: 'Asia/Seoul',
  funnels: {
    taste: [{ step: 'LANDING_VISIT', visitors: 120 }, { step: 'FEATURE_SELECT', visitors: 60 }],
    library: [{ step: 'LANDING_VISIT', visitors: 120 }, { step: 'LIBRARY_COMPLETE', visitors: 30 }],
  },
  trend: [
    { date: '2026-11-07', visitors: 10, testCompletes: 4, libraryCompletes: 1 },
    { date: '2026-11-08', visitors: 5, testCompletes: 2, libraryCompletes: 0 },
  ],
  distribution: {
    features: [{ code: 'TASTE', count: 9 }], tasteTypes: [{ code: 'EMCS', count: 3 }],
    libraryLedger: [], libraryCrime: [], libraryGenre: [], libraryRecordedSince: null,
  },
  recommendations: { totalImpressions: 0, contexts: [], topGames: [] },
  shares: {
    taste: { linkClicks: 4, imageClicks: 0, inflows: 2, inflowPerClick: 0.5 },
    library: { linkClicks: 0, imageClicks: 0, inflows: 0, inflowPerClick: null },
  },
};

beforeEach(() => {
  try { localStorage.clear(); } catch {}
  vi.mocked(fetchDashboard).mockReset();
});

afterEach(() => { cleanup(); });

describe('MetricsPage', () => {
  it('asks for a token when none is stored', async () => {
    render(<MetricsPage />);
    expect(await screen.findByLabelText('접근 토큰')).toBeInTheDocument();
  });

  it('loads 7d with the stored token and shows the summary and funnels', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard).mockResolvedValue(sample);
    render(<MetricsPage />);
    expect(await screen.findByLabelText('취향 테스트 퍼널')).toBeInTheDocument();
    expect(fetchDashboard).toHaveBeenCalledWith('secret', '7d');
    // 요약의 테스트 완료는 추이의 합(4 + 2)이다
    expect(screen.getByTestId('kpi-testCompletes')).toHaveTextContent('6');
    expect(screen.getByTestId('kpi-visitors')).toHaveTextContent('120');
  });

  it('reloads with the chosen range', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard).mockResolvedValue(sample);
    render(<MetricsPage />);
    await screen.findByLabelText('취향 테스트 퍼널');
    fireEvent.click(screen.getByRole('button', { name: '30일' }));
    await waitFor(() => expect(fetchDashboard).toHaveBeenLastCalledWith('secret', '30d'));
  });

  it('asks again and forgets the token on 401', async () => {
    localStorage.setItem(TOKEN_KEY, 'bad');
    vi.mocked(fetchDashboard).mockRejectedValue(new DashboardAuthError('unauthorized'));
    render(<MetricsPage />);
    expect(await screen.findByLabelText('접근 토큰')).toBeInTheDocument();
    expect(screen.getByText('토큰이 올바르지 않습니다')).toBeInTheDocument();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it('marks library verdicts as not recorded when nothing is recorded yet', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard).mockResolvedValue(sample);
    render(<MetricsPage />);
    expect(await screen.findByText('판정 기록 없음')).toBeInTheDocument();
  });

  it('shows ledger/crime/genre subtitles with Korean verdict names', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    const withVerdicts: Dashboard = {
      ...sample,
      distribution: {
        ...sample.distribution,
        libraryRecordedSince: sample.from,
        libraryLedger: [{ code: 'PRACTICAL', count: 5 }],
        libraryCrime: [{ code: 'GENRE_LOYALIST', count: 3 }],
        libraryGenre: [{ code: 'RPG_ADVENTURE', count: 2 }],
      },
    };
    vi.mocked(fetchDashboard).mockResolvedValue(withVerdicts);
    render(<MetricsPage />);
    await screen.findByLabelText('취향 테스트 퍼널');
    expect(screen.getByText('장부')).toBeInTheDocument();
    expect(screen.getByText('판결문')).toBeInTheDocument();
    expect(screen.getByText('장르')).toBeInTheDocument();
    expect(screen.getByText('실속파')).toBeInTheDocument();
  });

  it('shows an empty-state message when there are no top games', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard).mockResolvedValue(sample);
    render(<MetricsPage />);
    expect(await screen.findByText('아직 노출 데이터가 없습니다')).toBeInTheDocument();
  });

  it('ignores a stale response that arrives after a newer range request', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    let resolveStale!: (value: Dashboard) => void;
    let resolveFresh!: (value: Dashboard) => void;
    const stalePromise = new Promise<Dashboard>(resolve => { resolveStale = resolve; });
    const freshPromise = new Promise<Dashboard>(resolve => { resolveFresh = resolve; });
    vi.mocked(fetchDashboard)
      .mockResolvedValueOnce(sample) // 마운트 때 7일을 처음 조회함
      .mockImplementationOnce(() => stalePromise) // '30일'을 누름
      .mockImplementationOnce(() => freshPromise); // '전체'를 누름

    render(<MetricsPage />);
    await screen.findByLabelText('취향 테스트 퍼널');

    fireEvent.click(screen.getByRole('button', { name: '30일' }));
    await waitFor(() => expect(fetchDashboard).toHaveBeenCalledTimes(2));
    fireEvent.click(screen.getByRole('button', { name: '전체' }));
    await waitFor(() => expect(fetchDashboard).toHaveBeenCalledTimes(3));

    const staleResponse: Dashboard = {
      ...sample, range: '30d',
      funnels: { ...sample.funnels, taste: [{ step: 'LANDING_VISIT', visitors: 999 }] },
    };
    const freshResponse: Dashboard = {
      ...sample, range: 'all',
      funnels: { ...sample.funnels, taste: [{ step: 'LANDING_VISIT', visitors: 42 }] },
    };

    // 마지막 요청('전체')이 먼저 응답한다
    resolveFresh(freshResponse);
    await waitFor(() => expect(screen.getByTestId('kpi-visitors')).toHaveTextContent('42'));

    // 이전 요청('30일')이 늦게 응답해도 화면은 마지막 요청의 숫자를 유지해야 한다
    // act 로 늦은 응답의 후속 처리가 끝날 때까지 기다린 뒤 확인한다
    await act(async () => { resolveStale(staleResponse); await stalePromise; });
    expect(screen.getByTestId('kpi-visitors')).toHaveTextContent('42');
  });

  it('restores the range button to the range on screen when a later request fails', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard)
      .mockResolvedValueOnce(sample) // 마운트 때 7일을 처음 조회함
      .mockImplementationOnce(() => new Promise<Dashboard>(() => {})) // '30일'은 끝나지 않음
      .mockRejectedValueOnce(new Error('down')); // '전체'는 실패함

    render(<MetricsPage />);
    await screen.findByLabelText('취향 테스트 퍼널');

    fireEvent.click(screen.getByRole('button', { name: '30일' }));
    await waitFor(() => expect(fetchDashboard).toHaveBeenCalledTimes(2));
    fireEvent.click(screen.getByRole('button', { name: '전체' }));

    // 화면 숫자는 7일 그대로이니 버튼도 30일이 아니라 7일로 돌아와야 한다
    await screen.findByText('지표를 불러오지 못했습니다');
    expect(screen.getByRole('button', { name: '7일' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '30일' })).toHaveAttribute('aria-pressed', 'false');
  });
});
