import "@testing-library/jest-dom/vitest";
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import MetricsPage from './page'

vi.mock('@/lib/api', () => ({
  metricsOverview: vi.fn(),
  MetricsAuthError: class extends Error {},
}))

beforeEach(() => {
  try { localStorage.clear() } catch {}
})

describe('MetricsPage', () => {
  it('shows the token form when no token is stored', async () => {
    render(<MetricsPage />)
    expect(await screen.findByLabelText('접근 토큰')).toBeInTheDocument()
  })
})
