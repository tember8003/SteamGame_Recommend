import { afterEach, describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import TasteAtlas from '../taste/TasteAtlas';

vi.mock('next/navigation', () => ({ usePathname: () => '/ko/taste', useSearchParams: () => new URLSearchParams() }));
afterEach(cleanup);

describe('TasteAtlas', () => {
  it('lets visitors search, filter, recover from no matches and follow localized detail links', () => {
    render(<TasteAtlas locale="ko" />);
    expect(screen.getAllByRole('link', { name: /유형 보기/ })).toHaveLength(16);
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: '오버워치' } });
    expect(screen.getAllByRole('link', { name: /유형 보기/ })).toHaveLength(3);
    fireEvent.click(screen.getByRole('button', { name: '솔로' }));
    expect(screen.queryByRole('link', { name: /트레이서.*유형 보기/ })).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: '아무도 없음' } });
    expect(screen.getByText('찾는 캐릭터가 없어요.')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '검색 초기화' }));
    expect(screen.getAllByRole('link', { name: /유형 보기/ })).toHaveLength(16);
    expect(screen.getByRole('link', { name: /에이전트 47.*유형 보기/ })).toHaveAttribute('href', '/ko/taste/GMHS');
  });
  it('renders English identities and links', () => {
    render(<TasteAtlas locale="en" />);
    expect(screen.getByRole('heading', { name: 'Meet your player character' })).toBeVisible();
    expect(screen.getByRole('link', { name: /Zoya.*View type/ })).toHaveAttribute('href', '/en/taste/ENCP');
  });
});
