import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { StrictMode } from 'react';
import '@testing-library/jest-dom/vitest';
import fixture from './fixtures/library-contract.json';
import { ApiError } from '@/lib/api';
import { libraryAnalyze, libraryShare, libraryShared, type LibraryAnalysis, type LibrarySnapshot } from '@/lib/library/api';
import { recommendProfile } from '@/lib/api';
import { recordTasteEvent } from '@/lib/taste/api';
import { libraryImageSections } from '@/lib/library/export';
import * as imageExport from '@/lib/library/export';
import LibraryWorkbench from '../library/LibraryWorkbench';
import LibraryShared from '../library/LibraryShared';
import LibraryCards from '../library/LibraryCards';

vi.mock('@/lib/library/api', () => ({ libraryAnalyze: vi.fn(), libraryShare: vi.fn(), libraryShared: vi.fn() }));
vi.mock('@/lib/api', async original => ({ ...await original<typeof import('@/lib/api')>(), recommendProfile: vi.fn() }));
vi.mock('@/lib/taste/api', () => ({ recordTasteEvent: vi.fn() }));
const result = fixture.ko as LibraryAnalysis;
const enter = async () => {
  fireEvent.change(screen.getByLabelText('Steam ID 또는 프로필 주소'), { target: { value: 'custom-name' } });
  fireEvent.click(screen.getByRole('button', { name: '내 라이브러리 분석하기' }));
  await screen.findByRole('heading', { name: '수집품 창고' });
};
beforeEach(() => {
  vi.mocked(libraryAnalyze).mockResolvedValue(result);
  vi.mocked(libraryShared).mockResolvedValue(fixture.shared as LibrarySnapshot);
  vi.mocked(libraryShare).mockResolvedValue({ shareId: 'abcdefghijklmnopqrstuv' });
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: vi.fn(async () => {}) } });
});
afterEach(() => { cleanup(); vi.resetAllMocks(); vi.useRealTimers(); window.history.replaceState(null, '', '/'); });

describe('library views', () => {
  it('opens the collapsed input and focuses it when starting another analysis', async () => {
    render(<LibraryWorkbench locale="ko" />); await enter();
    const input = screen.getByLabelText('Steam ID 또는 프로필 주소');
    expect(input.closest('details')).not.toHaveAttribute('open');
    fireEvent.click(screen.getByRole('button', { name: '다른 기록 불러오기' }));
    expect(input.closest('details')).toHaveAttribute('open');
    expect(input).toHaveFocus();
  });
  it('saving an image does not create a public share', async () => {
    const save = vi.spyOn(imageExport, 'saveLibraryImage').mockResolvedValue('data:image/png;base64,fixture');
    render(<LibraryWorkbench locale="ko" />); await enter();
    fireEvent.click(screen.getByRole('button', { name: '내 거점 이미지 저장' }));
    await screen.findByText('이미지가 준비됐어요.');
    expect(save).toHaveBeenCalledWith(result, 'ko', undefined);
    expect(libraryShare).not.toHaveBeenCalled();
    expect(recordTasteEvent).toHaveBeenCalledWith('SHARE_CLICK', 'LIBRARY', 'IMAGE');
    save.mockRestore();
  });
  it('renders all four API cards and recommends using the resolved SteamID, not the input', async () => {
    vi.mocked(recommendProfile).mockResolvedValue({ usedTags: [], recommendedGame: [], review: [] });
    render(<LibraryWorkbench locale="ko" />);
    await enter();
    expect(libraryAnalyze).toHaveBeenCalledWith('custom-name', 'ko');
    for (const name of ['수집품 창고', '추억의 전시장', '캐릭터 상태창', '다음 모험 지도']) expect(screen.getByRole('tab', { name: new RegExp(name) })).toBeInTheDocument();
    expect(screen.getByText('안 해 본 게임이 10개로 전체의 40%입니다. 수집가의 기질이 보입니다.')).toBeInTheDocument();
    expect(screen.queryByText(result.profile.steamId64)).not.toBeInTheDocument();
    expect(recommendProfile).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '좋아할 만한 새 게임 찾기' }));
    await waitFor(() => expect(recommendProfile).toHaveBeenCalledWith(result.profile.steamId64, expect.anything()));
    expect(recordTasteEvent).not.toHaveBeenCalledWith('LIBRARY_COMPLETE', expect.anything());
  });
  it('creates no share before confirmation, and reuses its share ID on subsequent copies', async () => {
    render(<LibraryWorkbench locale="ko" />); await enter();
    fireEvent.click(screen.getByRole('button', { name: '거점 링크 공유' }));
    expect(libraryShare).not.toHaveBeenCalled();
    expect(screen.getByText(/누구나 90일 동안/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '확인했어요. 공유 링크 만들기' }));
    await screen.findByText('공유 링크를 복사했어요.');
    expect((screen.getByLabelText('공유 링크') as HTMLInputElement).value).toContain('/ko/library/share/abcdefghijklmnopqrstuv?s=1');
    fireEvent.click(screen.getByRole('button', { name: '거점 링크 공유' }));
    await waitFor(() => expect(navigator.clipboard.writeText).toHaveBeenCalledTimes(2));
    expect(libraryShare).toHaveBeenCalledTimes(1);
    expect(recordTasteEvent).toHaveBeenCalledWith('SHARE_CLICK', 'LIBRARY', 'LINK');
  });
  it('refreshes an expired analysis exactly once using the submitted input, even if the field changed', async () => {
    vi.mocked(libraryShare).mockRejectedValueOnce(new ApiError(404, 'ANALYSIS_EXPIRED')).mockResolvedValue({ shareId: 'abcdefghijklmnopqrstuv' });
    vi.mocked(libraryAnalyze).mockResolvedValueOnce(result).mockResolvedValue({ ...result, analysisId: 'new-analysis' });
    render(<LibraryWorkbench locale="ko" />); await enter();
    fireEvent.change(screen.getByLabelText('Steam ID 또는 프로필 주소'), { target: { value: 'someone-else' } });
    fireEvent.click(screen.getByRole('button', { name: '거점 링크 공유' }));
    fireEvent.click(screen.getByRole('button', { name: '확인했어요. 공유 링크 만들기' }));
    await screen.findByText('공유 링크를 복사했어요.');
    expect(libraryAnalyze).toHaveBeenLastCalledWith('custom-name', 'ko');
    expect(libraryShare).toHaveBeenNthCalledWith(2, 'new-analysis');
    expect(libraryAnalyze).toHaveBeenCalledTimes(2);
  });
  it('shows private-library guidance and locks retries for the 429 duration', async () => {
    vi.mocked(libraryAnalyze).mockRejectedValueOnce(new ApiError(403, 'LIBRARY_PRIVATE')).mockRejectedValueOnce(new ApiError(429, 'RATE_LIMITED', 3));
    render(<LibraryWorkbench locale="ko" />);
    fireEvent.change(screen.getByLabelText('Steam ID 또는 프로필 주소'), { target: { value: 'custom-name' } });
    fireEvent.click(screen.getByRole('button', { name: '내 라이브러리 분석하기' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('약 2분');
    vi.useFakeTimers();
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '내 라이브러리 분석하기' })); });
    expect(screen.getByRole('button', { name: '내 라이브러리 분석하기' })).toBeDisabled();
    await act(() => vi.advanceTimersByTimeAsync(3100));
    expect(screen.getByRole('button', { name: '내 라이브러리 분석하기' })).toBeEnabled();
  });
  it('shared results have no identity or recommendation action and only remove the inflow parameter once', async () => {
    window.history.replaceState(null, '', '/ko/library/share/abcdefghijklmnopqrstuv?s=1&keep=yes#cards');
    render(<StrictMode><LibraryShared locale="ko" shareId="abcdefghijklmnopqrstuv" /></StrictMode>);
    await screen.findByRole('heading', { name: '수집품 창고' });
    expect(screen.queryByText('Test Player')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '좋아할 만한 새 게임 찾기' })).not.toBeInTheDocument();
    expect(window.location.search).toBe('?keep=yes'); expect(window.location.hash).toBe('#cards');
    expect(vi.mocked(recordTasteEvent).mock.calls.filter(call => call[0] === 'SHARE_INFLOW')).toHaveLength(1);
  });
  it('shows insufficient evidence for null attributes, and image sections never include a profile', () => {
    const data = { ...result, gamerStats: result.gamerStats.map(stat => ({ ...stat, value: null })) };
    render(<LibraryCards data={data} locale="ko" />);
    fireEvent.click(screen.getByRole('tab', { name: /캐릭터 상태창/ }));
    expect(screen.getAllByText('기록 부족')).toHaveLength(5);
    expect(screen.queryByRole('meter')).not.toBeInTheDocument();
    const output = JSON.stringify(libraryImageSections(result, 'ko'));
    for (const secret of [result.profile.steamId64, result.profile.personaName, 'avatarUrl', 'analysisId']) expect(output).not.toContain(secret);
  });
});
