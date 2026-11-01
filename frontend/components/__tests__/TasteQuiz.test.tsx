import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import TasteQuiz from '../taste/TasteQuiz';
import { TasteSessionProvider } from '@/lib/taste/session';
import { tasteQuestions, tasteResult, recordTasteEvent } from '@/lib/taste/api';
import { ApiError } from '@/lib/api';
const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock('next/navigation', () => ({ useRouter: () => ({ push }) }));
vi.mock('@/lib/taste/api', async original => ({ ...await original<typeof import('@/lib/taste/api')>(), tasteQuestions: vi.fn(), tasteResult: vi.fn(), recordTasteEvent: vi.fn() }));
const fixtures = JSON.parse(readFileSync('components/__tests__/fixtures/taste-contract.json', 'utf8'));
const { questions, result } = fixtures;
beforeEach(() => { vi.clearAllMocks(); vi.mocked(tasteQuestions).mockResolvedValue(questions); vi.mocked(tasteResult).mockResolvedValue(result); });
afterEach(cleanup);
const mount = () => render(<TasteSessionProvider><TasteQuiz locale="ko" /></TasteSessionProvider>);
async function answerAll() {
  for (let n = 1; n <= 12; n++) {
    await screen.findByRole('heading', { name: new RegExp(`^${n}\\.`) });
    fireEvent.click(screen.getByTestId('answer-A'));
    fireEvent.click(screen.getByRole('button', { name: n === 12 ? '결과 보기' : '다음 문항' }));
  }
}
describe('TasteQuiz', () => {
  it('preserves previous answers and records TEST_START once, then submits exactly 12 answers', async () => {
    mount();
    await screen.findByTestId('answer-A');
    fireEvent.click(screen.getByTestId('answer-B'));
    fireEvent.click(screen.getByRole('button', { name: '다음 문항' }));
    fireEvent.click(screen.getByRole('button', { name: '이전 문항' }));
    expect(screen.getByTestId('answer-B')).toHaveAttribute('aria-pressed', 'true');
    await answerAll();
    await waitFor(() => expect(push).toHaveBeenCalledWith(`/ko/taste/${result.code}`));
    expect(tasteResult).toHaveBeenCalledWith(questions.version, Array(12).fill('A'), 'ko');
    expect(vi.mocked(recordTasteEvent).mock.calls.filter(call => call[0] === 'TEST_START')).toHaveLength(1);
    expect(vi.mocked(recordTasteEvent).mock.calls.some(call => String(call[0]) === 'TEST_COMPLETE')).toBe(false);
  });
  it('reloads uncached questions and clears answers after a 409 version conflict', async () => {
    vi.mocked(tasteResult).mockRejectedValue(new ApiError(409, 'TASTE_VERSION_MISMATCH'));
    mount(); await answerAll();
    await waitFor(() => expect(tasteQuestions).toHaveBeenCalledWith('ko', true));
    await screen.findByRole('heading', { name: /^1\./ });
    expect(screen.getByTestId('answer-A')).toHaveAttribute('aria-pressed', 'false');
    expect(push).not.toHaveBeenCalled();
  });
  it('keeps bypassing the stale cache if the first version-recovery request fails', async () => {
    vi.mocked(tasteQuestions).mockResolvedValueOnce(questions).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ ...questions, version: 'v2' });
    vi.mocked(tasteResult).mockRejectedValue(new ApiError(409, 'TASTE_VERSION_MISMATCH'));
    mount(); await answerAll();
    await screen.findByRole('alert');
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    await screen.findByRole('heading', { name: /^1\./ });
    expect(vi.mocked(tasteQuestions).mock.calls[2]?.[1]).toBe(true);
  });
  it('retains answers and prevents resubmission during a 429 cooldown', async () => {
    vi.mocked(tasteResult).mockRejectedValue(new ApiError(429, 'RATE_LIMITED', 12));
    mount(); await answerAll();
    const button = await screen.findByRole('button', { name: '12s' });
    expect(button).toBeDisabled();
    expect(screen.getByTestId('answer-A')).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(button);
    expect(tasteResult).toHaveBeenCalledTimes(1);
    expect(push).not.toHaveBeenCalled();
  });
});
