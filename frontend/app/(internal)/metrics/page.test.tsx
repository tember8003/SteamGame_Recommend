import "@testing-library/jest-dom/vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import MetricsPage from './page';
import { fetchDashboard, fetchStatus, DashboardAuthError } from './api';
import type { Dashboard, Status } from './types';

vi.mock('./api', () => ({
  fetchDashboard: vi.fn(),
  fetchStatus: vi.fn(),
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

const LONG_NOTE = 'pages=141 stop=end-of-results skipped=1 unknownTags=0 extra=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';

// 운영 상태 샘플, 서버 상태+이벤트 1건(장부 판정 있음)+오류 없음+수집 상태(경고 없음)
const sampleStatus: Status = {
  server: { revision: 'abcdef1234567890', startedAt: '2026-11-08T05:47:22Z', flywayVersion: '29' },
  recentEvents: [
    { at: '2026-11-08T05:47:22Z', type: 'LIBRARY_COMPLETE', ref: null, channel: null, ledgerCode: 'PRACTICAL', crimeCode: null, genreCode: null },
  ],
  errors: { since: '2026-11-08T05:47:22Z', counts: [], recent: [] },
  collection: {
    jobs: [
      {
        batchType: 'steam_catalog', lastStartedAt: '2026-11-20T05:30:00Z', lastFinishedAt: '2026-11-20T06:00:00Z',
        lastProcessed: 39958, lastFailed: 0, lastNote: LONG_NOTE, lastRunFailed: false, running: false, stuck: false,
        lastSuccessAt: '2026-11-20T06:00:00Z', lastSuccessProcessed: 39958, expectedIntervalHours: 26,
        stale: false, runs7d: 7, failedRuns7d: 0,
      },
      {
        batchType: 'tag_cooccurrence', lastStartedAt: '2026-11-20T06:00:00Z', lastFinishedAt: '2026-11-20T06:01:00Z',
        lastProcessed: 74389, lastFailed: 0, lastNote: 'pairs=74389', lastRunFailed: false, running: false, stuck: false,
        lastSuccessAt: '2026-11-20T06:01:00Z', lastSuccessProcessed: 74389, expectedIntervalHours: 26,
        stale: false, runs7d: 7, failedRuns7d: 0,
      },
      {
        batchType: 'detail_rolling', lastStartedAt: '2026-11-20T12:00:00Z', lastFinishedAt: null,
        lastProcessed: 0, lastFailed: 0, lastNote: null, lastRunFailed: false, running: true, stuck: false,
        lastSuccessAt: '2026-11-20T06:10:00Z', lastSuccessProcessed: 120, expectedIntervalHours: 7,
        stale: false, runs7d: 28, failedRuns7d: 0,
      },
      {
        batchType: 'embed_backfill', lastStartedAt: '2026-11-20T06:15:00Z', lastFinishedAt: '2026-11-20T06:20:00Z',
        lastProcessed: 1000, lastFailed: 0, lastNote: 'processed=1000 failed=0 (api_down)', lastRunFailed: false,
        running: false, stuck: false, lastSuccessAt: '2026-11-20T06:20:00Z', lastSuccessProcessed: 1000, expectedIntervalHours: 26,
        stale: false, runs7d: 7, failedRuns7d: 0,
      },
      {
        batchType: 'tag_embedding', lastStartedAt: null, lastFinishedAt: null, lastProcessed: null, lastFailed: null,
        lastNote: null, lastRunFailed: false, running: false, stuck: false, lastSuccessAt: null, lastSuccessProcessed: null,
        expectedIntervalHours: null, stale: false, runs7d: 0, failedRuns7d: 0,
      },
    ],
    data: {
      games: 44656, embeddingMissing: 12, tagVectorMissing: 300, reviewTextWrites24h: 950,
      lastFunnelEventAt: '2026-11-20T11:00:00Z', lastImpressionAt: null,
    },
    flags: [{ name: 'similarInterleave', enabled: false }],
    warnings: [],
  },
};

beforeEach(() => {
  try { localStorage.clear(); } catch {}
  vi.mocked(fetchDashboard).mockReset();
  vi.mocked(fetchStatus).mockReset();
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

  it('shows the three operations status cards with the short commit and the Flyway version', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard).mockResolvedValue(sample);
    vi.mocked(fetchStatus).mockResolvedValue(sampleStatus);
    render(<MetricsPage />);
    expect(await screen.findByLabelText('서버 상태')).toBeInTheDocument();
    expect(screen.getByLabelText('최근 이벤트')).toBeInTheDocument();
    expect(screen.getByLabelText('최근 오류')).toBeInTheDocument();
    // 커밋은 앞 7자리만 보인다
    expect(screen.getByText('abcdef1')).toBeInTheDocument();
    // 서버 상태 영역으로 범위를 좁힌다, Flyway 버전 '29' 가 다른 영역의 숫자와 헷갈리지 않게 한다
    expect(within(screen.getByLabelText('서버 상태')).getByText('29')).toBeInTheDocument();
  });

  it('shows the verdict code of a recent event as its Korean name', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard).mockResolvedValue(sample);
    vi.mocked(fetchStatus).mockResolvedValue(sampleStatus);
    render(<MetricsPage />);
    expect(await screen.findByText('실속파')).toBeInTheDocument();
  });

  it('shows a no-errors message when there have been no errors since startup', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard).mockResolvedValue(sample);
    vi.mocked(fetchStatus).mockResolvedValue(sampleStatus);
    render(<MetricsPage />);
    expect(await screen.findByText('기동 이후 오류 없음')).toBeInTheDocument();
  });

  it('keeps the dashboard numbers when only the status fetch fails', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard).mockResolvedValue(sample);
    vi.mocked(fetchStatus).mockRejectedValue(new Error('status down'));
    render(<MetricsPage />);
    expect(await screen.findByText('운영 상태를 불러오지 못했습니다')).toBeInTheDocument();
    expect(screen.getByTestId('kpi-visitors')).toHaveTextContent('120');
    expect(screen.queryByText('지표를 불러오지 못했습니다')).not.toBeInTheDocument();
  });

  it('goes back to the token screen when the status fetch returns 401', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard).mockResolvedValue(sample);
    vi.mocked(fetchStatus).mockRejectedValue(new DashboardAuthError('unauthorized'));
    render(<MetricsPage />);
    expect(await screen.findByLabelText('접근 토큰')).toBeInTheDocument();
    expect(screen.getByText('토큰이 올바르지 않습니다')).toBeInTheDocument();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it('keeps the token screen when a stale dashboard response arrives after a status 401', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    let resolveDashboard!: (value: Dashboard) => void;
    const dashboardPromise = new Promise<Dashboard>(resolve => { resolveDashboard = resolve; });
    vi.mocked(fetchDashboard).mockImplementation(() => dashboardPromise);
    vi.mocked(fetchStatus).mockRejectedValue(new DashboardAuthError('unauthorized'));

    render(<MetricsPage />);
    // 운영 상태 조회가 먼저 401 로 실패해 토큰 화면으로 간다
    expect(await screen.findByLabelText('접근 토큰')).toBeInTheDocument();

    // 늦게 도착하는 대시보드 응답(성공)이 토큰 화면을 다시 뒤집으면 안 된다
    // act 로 후속 처리(setState)까지 끝날 때까지 기다린 뒤 확인한다
    await act(async () => { resolveDashboard(sample); await dashboardPromise; });

    expect(screen.getByLabelText('접근 토큰')).toBeInTheDocument();
    expect(screen.queryByTestId('kpi-visitors')).not.toBeInTheDocument();
  });

  it('shows timestamps converted to Korean time regardless of the test runner timezone', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard).mockResolvedValue(sample);
    vi.mocked(fetchStatus).mockResolvedValue(sampleStatus);
    render(<MetricsPage />);
    // UTC 2026-11-08T05:47:22Z 는 한국 시간으로 11-08 14:47 이다
    expect(await screen.findByText('11-08 14:47')).toBeInTheDocument();
  });

  it('shows the collection section with no warnings, Korean job names, data figures and the flag', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard).mockResolvedValue(sample);
    vi.mocked(fetchStatus).mockResolvedValue(sampleStatus);
    render(<MetricsPage />);
    const section = await screen.findByLabelText('수집');
    expect(within(section).getByText('경고 없음')).toBeInTheDocument();

    const jobs = within(section).getByLabelText('배치 작업');
    const catalogRow = within(jobs).getByText('카탈로그 수집').closest('tr')!;
    expect(within(catalogRow).getByText('39,958 / 0')).toBeInTheDocument();
    expect(within(catalogRow).getByText('정상')).toBeInTheDocument();
    // 기록이 없는 작업은 칸을 비우고 기록 없음으로 보인다
    const gnnRow = within(jobs).getByText('GNN 학습').closest('tr')!;
    expect(within(gnnRow).getByText('기록 없음')).toBeInTheDocument();
    const detailRow = within(jobs).getByText('상세 수집').closest('tr')!;
    expect(within(detailRow).getByText('실행 중')).toBeInTheDocument();

    const data = within(section).getByLabelText('실제 데이터');
    expect(within(data).getByText('44,656')).toBeInTheDocument();
    expect(within(data).getByText('GNN 벡터 없는 게임').nextSibling).toHaveTextContent('300');
    // 마지막 퍼널 이벤트 UTC 2026-11-20T11:00:00Z 는 한국 시간으로 11-20 20:00 이다
    expect(within(data).getByText('2026-11-20 20:00')).toBeInTheDocument();
    expect(within(data).getByText('마지막 추천 노출').nextSibling).toHaveTextContent('-');

    expect(within(section).getByLabelText('기능 설정')).toHaveTextContent('인터리빙꺼짐');
  });

  it('lists each collection warning in Korean and marks failing jobs', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard).mockResolvedValue(sample);
    const jobs = sampleStatus.collection.jobs.map(j =>
      j.batchType === 'embed_backfill' ? { ...j, stale: true }
        : j.batchType === 'tag_cooccurrence' ? { ...j, lastRunFailed: true }
        : j.batchType === 'tag_embedding' ? { ...j, lastStartedAt: '2026-11-18T00:00:00Z', stuck: true }
        : j);
    vi.mocked(fetchStatus).mockResolvedValue({
      ...sampleStatus,
      collection: {
        ...sampleStatus.collection,
        jobs,
        warnings: [
          { code: 'JOB_STUCK', batchType: 'tag_embedding' },
          { code: 'JOB_FAILED', batchType: 'tag_cooccurrence' },
          { code: 'JOB_STALE', batchType: 'embed_backfill' },
          { code: 'CATALOG_DROP', batchType: 'steam_catalog' },
          { code: 'DETAIL_NO_WRITES', batchType: 'detail_rolling' },
        ],
      },
    });
    render(<MetricsPage />);
    const warnings = await screen.findByLabelText('수집 경고');
    expect(within(warnings).getByText('GNN 학습 24시간 넘게 끝나지 않음 (멈춤 추정)')).toBeInTheDocument();
    expect(within(warnings).getByText('태그 쌍 마지막 실행 실패')).toBeInTheDocument();
    expect(within(warnings).getByText('임베딩 주기 초과')).toBeInTheDocument();
    expect(within(warnings).getByText('카탈로그 저장 수 5% 넘게 감소')).toBeInTheDocument();
    expect(within(warnings).getByText('상세 수집이 돌았는데 24시간 동안 저장된 리뷰 본문 0')).toBeInTheDocument();
    expect(within(warnings).queryByText('경고 없음')).not.toBeInTheDocument();

    const table = screen.getByLabelText('배치 작업');
    expect(within(within(table).getByText('태그 쌍').closest('tr')!).getByText('실패')).toBeInTheDocument();
    expect(within(within(table).getByText('임베딩').closest('tr')!).getByText('주기 초과')).toBeInTheDocument();
    expect(within(within(table).getByText('GNN 학습').closest('tr')!).getByText('멈춤 추정')).toBeInTheDocument();
  });

  it('cuts a long job note and keeps the full note in the title', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard).mockResolvedValue(sample);
    vi.mocked(fetchStatus).mockResolvedValue(sampleStatus);
    render(<MetricsPage />);
    const table = await screen.findByLabelText('배치 작업');
    const row = within(table).getByText('카탈로그 수집').closest('tr')!;
    const note = within(row).getByTitle(LONG_NOTE);
    expect(note.textContent!.length).toBeLessThan(LONG_NOTE.length);
    expect(note.textContent!.endsWith('…')).toBe(true);
  });

  it('shows the interleaving flag as on when it is enabled', async () => {
    localStorage.setItem(TOKEN_KEY, 'secret');
    vi.mocked(fetchDashboard).mockResolvedValue(sample);
    vi.mocked(fetchStatus).mockResolvedValue({
      ...sampleStatus,
      collection: { ...sampleStatus.collection, flags: [{ name: 'similarInterleave', enabled: true }] },
    });
    render(<MetricsPage />);
    expect(await screen.findByLabelText('기능 설정')).toHaveTextContent('인터리빙켜짐');
  });
});
