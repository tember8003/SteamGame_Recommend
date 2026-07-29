import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { sgrFeedback } from '../feedback'

const API_BASE = 'http://localhost:8888'
const GENERATED_UUID = '11111111-1111-1111-1111-111111111111'

function stubLocalStorage(initial: Record<string, string> = {}) {
  const store = new Map(Object.entries(initial))
  const localStorageMock = {
    getItem: vi.fn((key: string) => store.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store.set(key, value)
    }),
    removeItem: vi.fn((key: string) => store.delete(key)),
    clear: vi.fn(() => store.clear()),
  }
  vi.stubGlobal('localStorage', localStorageMock)
  return localStorageMock
}

function stubFetch() {
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

beforeEach(() => {
  process.env.NEXT_PUBLIC_API_BASE = API_BASE
  vi.stubGlobal('crypto', { randomUUID: () => GENERATED_UUID })
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('client id', () => {
  it('저장된 client_id 가 없으면 생성해서 저장한다', () => {
    const localStorageMock = stubLocalStorage()
    const fetchMock = stubFetch()

    sgrFeedback(400, 'UP', 'INPUT')

    expect(localStorageMock.getItem).toHaveBeenCalledWith('sgr_client_id')
    expect(localStorageMock.setItem).toHaveBeenCalledWith('sgr_client_id', GENERATED_UUID)

    const [, options] = fetchMock.mock.calls[0]
    const body = JSON.parse(options.body as string)
    expect(body.clientId).toBe(GENERATED_UUID)
  })

  it('저장된 client_id 가 있으면 재사용하고 다시 저장하지 않는다', () => {
    const localStorageMock = stubLocalStorage({ sgr_client_id: 'existing-client-id' })
    const fetchMock = stubFetch()

    sgrFeedback(400, 'UP', 'INPUT')

    expect(localStorageMock.setItem).not.toHaveBeenCalled()

    const [, options] = fetchMock.mock.calls[0]
    const body = JSON.parse(options.body as string)
    expect(body.clientId).toBe('existing-client-id')
  })
})

describe('전송', () => {
  it('올바른 경로, 바디, keepalive 로 POST 한다', () => {
    stubLocalStorage({ sgr_client_id: 'existing-client-id' })
    const fetchMock = stubFetch()

    sgrFeedback(400, 'CLICK', 'SIMILAR', 2, '76561198000000000')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe(`${API_BASE}/api/recommend/feedback`)
    expect(options.method).toBe('POST')
    expect(options.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(options.keepalive).toBe(true)
    expect(JSON.parse(options.body as string)).toEqual({
      clientId: 'existing-client-id',
      appid: 400,
      signal: 'CLICK',
      context: 'SIMILAR',
      rankPosition: 2,
      steamId: '76561198000000000',
    })
  })

  it('rank, steamId 를 생략하면 null 로 보낸다', () => {
    stubLocalStorage({ sgr_client_id: 'existing-client-id' })
    const fetchMock = stubFetch()

    sgrFeedback(400, 'DOWN', 'RANDOM')

    const [, options] = fetchMock.mock.calls[0]
    expect(JSON.parse(options.body as string)).toEqual({
      clientId: 'existing-client-id',
      appid: 400,
      signal: 'DOWN',
      context: 'RANDOM',
      rankPosition: null,
      steamId: null,
    })
  })

  it('fetch 가 실패해도 예외를 던지지 않는다', () => {
    stubLocalStorage({ sgr_client_id: 'existing-client-id' })
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(new Error('network down')),
    )

    expect(() => sgrFeedback(400, 'UP', 'INPUT')).not.toThrow()
  })
})
