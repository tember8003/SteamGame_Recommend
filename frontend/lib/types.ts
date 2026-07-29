// 백엔드 DTO 를 그대로 옮긴 타입을 모은다
// 필드명은 백엔드 JSON 직렬화명과 동일하게 맞춘다(예: korean_check, free_check 는 실제로 스네이크케이스 필드임)

// ── graph ──

export interface GraphNode {
  appid: number
  name: string
  image: string
}

export interface GraphNeighbor {
  appid: number
  name: string
  image: string
  genres: string
  similarity: number
}

export interface NeighborsResponse {
  appid: number
  neighbors: GraphNeighbor[]
}

// ── game ──

export interface GameSearchResult {
  gameName: string
  headerImage: string
}

// ── recommendation ──

export interface SteamApp {
  appid: number
  name: string
  shortDescription?: string
  headerImage: string
  steamStore?: string
}

export interface ReviewResponse {
  positiveReview?: string
  negativeReview?: string
  negativeHours?: number
  positiveHours?: number
  reviewSummary?: string
  positiveRating?: number
}

export interface RecommendationResult {
  usedTags: string[]
  recommendedGame: SteamApp[]
  review: ReviewResponse[]
}

// recommend/* 요청 공통 옵션, review 는 최소 리뷰 수, koreanCheck, freeCheck 는 null 을 허용함
export interface RecommendOptions {
  review: number
  koreanCheck?: boolean | null
  freeCheck?: boolean | null
  excludeAppids?: number[]
}

export interface RecommendRandomOptions extends RecommendOptions {
  excludedTag?: string[] | null
}

// ── choice ──

export interface ChoiceRound {
  roundIndex: number
  game1: RecommendationResult
  game2: RecommendationResult
}

export interface ChoiceStartResponse {
  sessionId: string
  rounds: ChoiceRound[]
}

export interface ChoiceSelectionResponse {
  success: boolean
  completed: boolean
  nextRoundIndex: number
}

export interface ChoiceResult {
  sessionId: string
  usedTags: string[]
  recommendedGames: SteamApp[]
  selectedGames: number[]
}

export interface ChoiceStatus {
  sessionId: string
  currentRound: number
  totalRounds: number
  completed: boolean
}

// ── review guess ──

export interface ReviewGuessGame {
  appid: number
  name: string
  imageUrl: string
  description?: string
}

export interface ReviewGuessRound {
  roundIndex: number
  positiveReview: string
  negativeReview: string
  gameOptions: ReviewGuessGame[]
  // 정답은 클라이언트에는 숨겨져 내려오므로 응답에서는 없을 수 있음
  correctAnswerId?: number
}

export interface ReviewGuessStartResponse {
  sessionId: string
  rounds: ReviewGuessRound[]
}

export interface ReviewGuessAnswerResponse {
  correct: boolean
  gameCompleted: boolean
  nextRoundIndex: number
  correctAnswerId?: number
  correctGameName: string
  currentScore: number
}

export interface ReviewGuessRoundResult {
  roundIndex: number
  correct: boolean
  selectedGameId: number
  selectedGameName: string
  correctGameId: number
  correctGameName: string
}

export interface ReviewGuessResult {
  sessionId: string
  correctAnswers: number
  totalRounds: number
  accuracy: number
  roundResults: ReviewGuessRoundResult[]
  grade: string
}

export interface ReviewGuessStatus {
  sessionId: string
  currentRound: number
  totalRounds: number
  completed: boolean
  currentScore: number
}

// ── feedback ──

// 피드백이 발생한 추천 플로우를 나타냄
export type FeedbackContext = 'INPUT' | 'PROFILE' | 'RECENT' | 'SIMILAR' | 'RANDOM'

// 추천 반응 신호, CLICK 은 스토어 이동(약한 양성 후보)으로 UP 과 구분함
export type FeedbackSignal = 'UP' | 'DOWN' | 'CLICK'

export interface FeedbackRequest {
  clientId: string
  steamId?: string | null
  appid: number
  context: FeedbackContext
  signal: FeedbackSignal
  rankPosition?: number | null
}
