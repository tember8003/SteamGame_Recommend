import { afterEach, describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import fixture from './fixtures/taste-contract.json';
import TasteDetail from '../taste/TasteDetail';
import type { TasteResult } from '@/lib/taste/api';
import TastePortrait from '../taste/TastePortrait';
import { getTasteType } from '@/lib/taste/catalog';

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('TasteDetail', () => {
  it('copies a public code-only URL and never pretends a profile is a scored test result', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    render(<TasteDetail type={getTasteType('ENCP')!} locale="ko" />);
    fireEvent.click(screen.getByRole('button', { name: '이 유형 링크 복사' }));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/ko/taste/ENCP?s=1`));
    expect(await screen.findByText('링크를 복사했어요.')).toBeVisible();
    expect(screen.getByText(/실제 테스트 결과가 아닙니다/)).toBeVisible();
    expect(screen.getByRole('link', { name: /원작 게임 보기/ })).toHaveAttribute('href', 'https://store.steampowered.com/app/690640/');
  });
  it('offers a selectable URL when the clipboard is denied', async () => {
    vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockRejectedValue(new Error('Denied')) } });
    render(<TasteDetail type={getTasteType('GMHS')!} locale="en" />);
    fireEvent.click(screen.getByRole('button', { name: 'Copy type link' }));
    expect(await screen.findByRole('textbox', { name: 'Share URL' })).toHaveValue(`${window.location.origin}/en/taste/GMHS?s=1`);
    expect(screen.getByRole('link', { name: 'View all 16 types' })).toHaveAttribute('href', '/en/taste');
  });
  it('retains the character identity when an image cannot load', () => {
    render(<TastePortrait code="GMHS" name="Agent 47" />);
    fireEvent.error(screen.getByRole('img', { name: 'Agent 47' }));
    expect(screen.getByRole('img', { name: 'Agent 47' })).toHaveTextContent('Agent 47');
  });
  it('renders v2 percentages and summaries instead of the v1 denominator', () => {
    const result = { ...fixture.result, version: 'v2', code: 'GMHS', axisScores: { EG: { E: 5, G: 7 }, NM: { N: 0, M: 12 }, HC: { H: 12, C: 0 }, SP: { S: 12, P: 0 } }, axisDetails: [{ id: 'EG', left: 'E', right: 'G', total: 12, leftPercent: 42, rightPercent: 58, winner: 'G', balance: 'BALANCED', tieBroken: false, summary: '탐험과 성장을 함께 즐기는 편이에요.' }] } as TasteResult;
    render(<TasteDetail type={getTasteType('GMHS')!} result={result} locale="ko" />);
    expect(screen.getByText('42%')).toBeVisible();
    expect(screen.getByText('58%')).toBeVisible();
    expect(screen.getByText('탐험과 성장을 함께 즐기는 편이에요.')).toBeVisible();
    expect(screen.queryByText(/\/ 3/)).not.toBeInTheDocument();
    expect(screen.getByText(/링크에는 캐릭터 유형만/)).toBeVisible();
  });

});
