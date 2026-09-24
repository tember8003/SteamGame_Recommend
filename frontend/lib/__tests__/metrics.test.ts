import { afterEach, describe, expect, it, vi } from 'vitest'
import { metricsOverview, MetricsAuthError } from '../api'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('metricsOverview', () => {
  it('sends the X-Metrics-Token header', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ totalImpressions: 0, contexts: [], topGames: [] }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await metricsOverview('secret')

    const init = fetchMock.mock.calls[0][1]
    expect(init.headers['X-Metrics-Token']).toBe('secret')
  })

  it('throws MetricsAuthError on 401', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }))

    await expect(metricsOverview('bad')).rejects.toBeInstanceOf(MetricsAuthError)
  })
})
