import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { cleanup } from '@testing-library/react';
import { useEffect } from 'react';
import { readFileSync } from 'node:fs';
import TasteAtlasLoader from '../taste/TasteAtlasLoader';
import TasteTypeLoader from '../taste/TasteTypeLoader';
import TasteGames from '../taste/TasteGames';
import { TasteSessionProvider, useTasteSession } from '@/lib/taste/session';
import { recordTasteEvent, tasteSummaries, tasteType, tasteRecommend } from '@/lib/taste/api';
import { ApiError } from '@/lib/api';
import { sgrFeedback } from '@/lib/feedback';
vi.mock('next/navigation', () => ({ usePathname: () => '/ko/taste', useRouter: () => ({ push: vi.fn() }) }));
vi.mock('@/lib/feedback', () => ({ sgrFeedback: vi.fn() }));
vi.mock('@/lib/taste/api', async original => ({ ...await original<typeof import('@/lib/taste/api')>(), tasteSummaries: vi.fn(), tasteType: vi.fn(), tasteRecommend: vi.fn(), recordTasteEvent: vi.fn() }));
const fixtures = JSON.parse(readFileSync('components/__tests__/fixtures/taste-contract.json', 'utf8'));
const { result, recommendation } = fixtures;
const view = result.type;
beforeEach(() => { vi.clearAllMocks(); vi.mocked(tasteType).mockResolvedValue(view); window.history.replaceState({}, '', '/ko/taste/GMHS'); });
afterEach(cleanup);
describe('API driven taste views', () => {
  it('uses API names and recovers a failed list request without showing a stale local list', async () => {
    vi.mocked(tasteSummaries).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([{ code: 'GMHS', name: '서버에서 바꾼 이름', work: '서버 작품', oneLiner: '서버 문구' }]);
    render(<TasteAtlasLoader locale="ko" />);
    await screen.findByRole('alert');
    expect(screen.queryByRole('heading', { name: '에이전트 47' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(await screen.findByRole('heading', { name: '서버에서 바꾼 이름' })).toBeVisible();
  });
  it('loads a shared type, removes the inflow flag, and sends no impression-free click feedback', async () => {
    window.history.replaceState({}, '', '/ko/taste/GMHS?s=1&other=ok');
    render(<TasteTypeLoader code="GMHS" locale="ko" />);
    await screen.findByRole('heading', { name: view.name });
    expect(tasteType).toHaveBeenCalledTimes(1);
    expect(recordTasteEvent).toHaveBeenCalledWith('SHARE_INFLOW', 'GMHS');
    expect(window.location.search).toBe('?other=ok');
    fireEvent.click(screen.getAllByRole('link').find(link => link.getAttribute('href') === view.featuredGames[0].steamStore)!);
    expect(sgrFeedback).not.toHaveBeenCalled();
  });
  it('uses the fresh POST result without an extra type GET', async () => {
    function Fresh() {
      const session = useTasteSession();
      useEffect(() => { if (!session.result) session.save(result, 'ko') }, [session]);
      return session.result ? <TasteTypeLoader code={result.code} locale="ko" /> : null;
    }
    render(<TasteSessionProvider><Fresh /></TasteSessionProvider>);
    await screen.findByText('당신의 게이머 유형');
    expect(tasteType).not.toHaveBeenCalled();
    expect(screen.getAllByRole('meter')).toHaveLength(8);
    fireEvent.click(screen.getAllByRole('link').find(link => link.getAttribute('href') === view.featuredGames[0].steamStore)!);
    expect(sgrFeedback).toHaveBeenCalledWith(view.featuredGames[0].appid, 'CLICK', 'TASTE', 0, undefined);
  });
  it('keeps more enabled after a short batch, excludes seen games, and stops only on NO_MORE', async () => {
    vi.mocked(tasteRecommend).mockResolvedValueOnce(recommendation).mockRejectedValueOnce(new ApiError(404, 'TASTE_NO_MORE_GAMES'));
    render(<TasteGames view={view} locale="ko" />);
    fireEvent.click(screen.getByRole('button', { name: '다른 게임 더 추천받기' }));
    await screen.findByRole('heading', { name: recommendation.games[0].name });
    const button = screen.getByRole('button', { name: '다른 게임 더 추천받기' });
    expect(button).toBeEnabled(); fireEvent.click(button);
    await screen.findByText('지금 보여드릴 게임을 모두 살펴봤어요.');
    expect(tasteRecommend).toHaveBeenLastCalledWith(view.code, expect.arrayContaining(recommendation.games.map((game: { appid: number }) => game.appid)));
    expect(screen.queryByRole('button', { name: '다른 게임 더 추천받기' })).not.toBeInTheDocument();
  });
  it('treats an explicit shared entry as a shared view even when this tab holds the same result', async () => {
    window.history.replaceState({}, '', '/ko/taste/GMHS?s=1');
    function SharedWithMemory() {
      const session = useTasteSession();
      useEffect(() => { if (!session.result) session.save(result, 'ko') }, [session]);
      return session.result ? <TasteTypeLoader code={result.code} locale="ko" /> : null;
    }
    render(<TasteSessionProvider><SharedWithMemory /></TasteSessionProvider>);
    await screen.findByRole('heading', { name: view.name });
    expect(tasteType).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('당신의 게이머 유형')).not.toBeInTheDocument();
  });
  it('does not hide more after an unrelated 404', async () => {
    vi.mocked(tasteRecommend).mockRejectedValue(new ApiError(404, 'TASTE_TYPE_NOT_FOUND'));
    render(<TasteGames view={view} locale="ko" />);
    fireEvent.click(screen.getByRole('button', { name: '다른 게임 더 추천받기' }));
    await waitFor(() => expect(screen.getByRole('alert')).toBeVisible());
    expect(screen.getByRole('button', { name: '다른 게임 더 추천받기' })).toBeEnabled();
  });
});
