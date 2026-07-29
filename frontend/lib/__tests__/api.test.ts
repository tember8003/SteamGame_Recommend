import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  choiceResult,
  choiceSelect,
  choiceStart,
  gamesSearch,
  graphNeighbors,
  graphSearch,
  recommendInput,
  recommendProfile,
  recommendRandom,
  recommendRecent,
  recommendSimilar,
  reviewGuessAnswer,
  reviewGuessResult,
  reviewGuessStart,
} from '../api'

const API_BASE = 'http://localhost:8888'

function stubFetchOnce(responseBody: unknown, ok = true, status = 200) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok,
    status,
    json: () => Promise.resolve(responseBody),
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

beforeEach(() => {
  process.env.NEXT_PUBLIC_API_BASE = API_BASE
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('graph', () => {
  it('graphSearch 는 q 쿼리로 GET 요청하고 결과를 그대로 반환한다', async () => {
    const nodes = [{ appid: 1, name: 'Portal', image: 'img.jpg' }]
    const fetchMock = stubFetchOnce(nodes)

    const result = await graphSearch('portal')

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/graph/search?q=portal`)
    expect(result).toEqual(nodes)
  })

  it('graphSearch 는 쿼리 문자열을 인코딩한다', async () => {
    const fetchMock = stubFetchOnce([])

    await graphSearch('half life')

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/graph/search?q=half%20life`)
  })

  it('graphNeighbors 는 appid, k 쿼리로 GET 요청한다', async () => {
    const response = { appid: 400, neighbors: [] }
    const fetchMock = stubFetchOnce(response)

    const result = await graphNeighbors(400, 8)

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/graph/neighbors?appid=400&k=8`)
    expect(result).toEqual(response)
  })

  it('graphNeighbors 는 k 를 생략할 수 있다', async () => {
    const fetchMock = stubFetchOnce({ appid: 400, neighbors: [] })

    await graphNeighbors(400)

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/graph/neighbors?appid=400`)
  })
})

describe('games', () => {
  it('gamesSearch 는 query 쿼리로 GET 요청한다', async () => {
    const games = [{ gameName: 'Portal 2', headerImage: 'img.jpg' }]
    const fetchMock = stubFetchOnce(games)

    const result = await gamesSearch('portal')

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/games/search?query=portal`)
    expect(result).toEqual(games)
  })
})

describe('recommend', () => {
  it('recommendInput 은 text 요청 바디로 POST 한다', async () => {
    const recResult = { usedTags: ['RPG'], recommendedGame: [], review: [] }
    const fetchMock = stubFetchOnce(recResult)

    const result = await recommendInput('아무 텍스트', {
      review: 1000,
      koreanCheck: true,
      freeCheck: false,
    })

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/recommend/input`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        text: '아무 텍스트',
        review: 1000,
        korean_check: true,
        free_check: false,
        excludeAppids: undefined,
      }),
    })
    expect(result).toEqual(recResult)
  })

  it('recommendProfile 은 steamId 요청 바디로 POST 한다', async () => {
    const fetchMock = stubFetchOnce({ usedTags: [], recommendedGame: [], review: [] })

    await recommendProfile('76561198000000000', { review: 500, koreanCheck: null, freeCheck: null })

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/recommend/profile`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        steamId: '76561198000000000',
        review: 500,
        korean_check: null,
        free_check: null,
        excludeAppids: undefined,
      }),
    })
  })

  it('recommendRecent 는 steamId 요청 바디로 recent-play 에 POST 한다', async () => {
    const fetchMock = stubFetchOnce({ usedTags: [], recommendedGame: [], review: [] })

    await recommendRecent('76561198000000000', { review: 500 })

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/recommend/recent-play`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        steamId: '76561198000000000',
        review: 500,
        korean_check: null,
        free_check: null,
        excludeAppids: undefined,
      }),
    })
  })

  it('recommendSimilar 는 gameName 요청 바디로 POST 한다', async () => {
    const fetchMock = stubFetchOnce({ usedTags: [], recommendedGame: [], review: [] })

    await recommendSimilar('Portal 2', { review: 100, excludeAppids: [1, 2] })

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/recommend/similar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        gameName: 'Portal 2',
        review: 100,
        korean_check: null,
        free_check: null,
        excludeAppids: [1, 2],
      }),
    })
  })

  it('recommendRandom 은 tags, excludedTag 요청 바디로 POST 한다', async () => {
    const fetchMock = stubFetchOnce({ usedTags: [], recommendedGame: [], review: [] })

    await recommendRandom(['RPG', 'Indie'], {
      review: 0,
      koreanCheck: true,
      freeCheck: true,
      excludedTag: ['Horror'],
    })

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/recommend/random`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        tags: ['RPG', 'Indie'],
        review: 0,
        korean_check: true,
        free_check: true,
        excludeAppids: undefined,
        excludedTag: ['Horror'],
      }),
    })
  })

  it('recommendRandom 은 excludedTag 를 생략하면 null 로 보낸다', async () => {
    const fetchMock = stubFetchOnce({ usedTags: [], recommendedGame: [], review: [] })

    await recommendRandom(['RPG'], { review: 0 })

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/recommend/random`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        tags: ['RPG'],
        review: 0,
        korean_check: null,
        free_check: null,
        excludeAppids: undefined,
        excludedTag: null,
      }),
    })
  })
})

describe('choice', () => {
  it('choiceStart 는 빈 바디로 POST 한다', async () => {
    const startResponse = { sessionId: 'abc', rounds: [] }
    const fetchMock = stubFetchOnce(startResponse)

    const result = await choiceStart()

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/choice/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    })
    expect(result).toEqual(startResponse)
  })

  it('choiceSelect 는 sessionId, roundIndex, selectedGameId 로 POST 한다', async () => {
    const selection = { success: true, completed: false, nextRoundIndex: 1 }
    const fetchMock = stubFetchOnce(selection)

    const result = await choiceSelect('abc', 0, 400)

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/choice/select`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId: 'abc', roundIndex: 0, selectedGameId: 400 }),
    })
    expect(result).toEqual(selection)
  })

  it('choiceResult 는 sessionId, review, korean_check, free_check 로 POST 한다', async () => {
    const choiceRes = { sessionId: 'abc', usedTags: [], recommendedGames: [], selectedGames: [] }
    const fetchMock = stubFetchOnce(choiceRes)

    const result = await choiceResult('abc', 1000, null, null)

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/choice/result`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId: 'abc', review: 1000, korean_check: null, free_check: null }),
    })
    expect(result).toEqual(choiceRes)
  })
})

describe('review-guess', () => {
  it('reviewGuessStart 는 빈 바디로 POST 한다', async () => {
    const startResponse = { sessionId: 'abc', rounds: [] }
    const fetchMock = stubFetchOnce(startResponse)

    const result = await reviewGuessStart()

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/review-guess/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    })
    expect(result).toEqual(startResponse)
  })

  it('reviewGuessAnswer 는 sessionId, roundIndex, selectedGameId 로 POST 한다', async () => {
    const answer = {
      correct: true,
      gameCompleted: false,
      nextRoundIndex: 1,
      correctGameName: 'Portal 2',
      currentScore: 1,
    }
    const fetchMock = stubFetchOnce(answer)

    const result = await reviewGuessAnswer('abc', 0, 400)

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/review-guess/answer`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId: 'abc', roundIndex: 0, selectedGameId: 400 }),
    })
    expect(result).toEqual(answer)
  })

  it('reviewGuessResult 는 sessionId 경로로 GET 요청한다', async () => {
    const resultBody = {
      sessionId: 'abc',
      correctAnswers: 5,
      totalRounds: 7,
      accuracy: 71.4,
      roundResults: [],
      grade: 'B',
    }
    const fetchMock = stubFetchOnce(resultBody)

    const result = await reviewGuessResult('abc')

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/api/review-guess/abc/result`)
    expect(result).toEqual(resultBody)
  })
})

describe('오류 처리', () => {
  it('응답이 실패하면 에러를 던진다', async () => {
    stubFetchOnce({}, false, 500)

    await expect(graphSearch('x')).rejects.toThrow()
  })
})
