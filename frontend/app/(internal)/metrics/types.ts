// 관리자 대시보드 응답 타입, 백엔드 MetricsDashboardDTO 와 같은 모양이다
export type Range = 'today' | '7d' | '30d' | 'all';
export interface Step { step: string; visitors: number }
export interface Daily { date: string; visitors: number; testCompletes: number; libraryCompletes: number }
export interface CodeCount { code: string; count: number }
export interface ContextMetric { context: string; impressions: number; up: number; down: number; click: number; upRate: number }
export interface TopGame { appid: number; name: string | null; impressions: number }
export interface ShareStat { linkClicks: number; imageClicks: number; inflows: number; inflowPerClick: number | null }
export interface Dashboard {
  range: Range; from: string; to: string; timezone: string;
  funnels: { taste: Step[]; library: Step[] };
  trend: Daily[];
  distribution: {
    features: CodeCount[]; tasteTypes: CodeCount[]; libraryLedger: CodeCount[];
    libraryCrime: CodeCount[]; libraryGenre: CodeCount[]; libraryRecordedSince: string | null;
  };
  recommendations: { totalImpressions: number; contexts: ContextMetric[]; topGames: TopGame[] };
  shares: { taste: ShareStat; library: ShareStat };
}

// 관리자 운영 상태 응답 타입, 백엔드 MetricsStatusDTO 와 같은 모양이다, 기간과 무관한 지금 상태다
export interface StatusServer { revision: string; startedAt: string; flywayVersion: string | null }
export interface StatusEvent {
  at: string; type: string; ref: string | null; channel: string | null;
  ledgerCode: string | null; crimeCode: string | null; genreCode: string | null;
}
export interface StatusCodeCount { status: number; errorCode: string; count: number }
export interface StatusErrorEntry { at: string; status: number; errorCode: string; route: string }
export interface StatusErrors { since: string; counts: StatusCodeCount[]; recent: StatusErrorEntry[] }
// 수집 상태, 작업이 남긴 기록(jobs)과 실제 DB 상태(data)를 나란히 보여 주려고 둘 다 담는다
// 시각과 개수 칸은 기록이 없으면 null 이다, stuck 은 24시간 넘게 끝나지 않은 실행이고 이때 running 은 false 다
export interface CollectionJob {
  batchType: string;
  lastStartedAt: string | null; lastFinishedAt: string | null;
  lastProcessed: number | null; lastFailed: number | null; lastNote: string | null;
  lastRunFailed: boolean; running: boolean; stuck: boolean;
  lastSuccessAt: string | null; lastSuccessProcessed: number | null;
  expectedIntervalHours: number | null; stale: boolean;
  runs7d: number; failedRuns7d: number;
}
export interface CollectionData {
  games: number; embeddingMissing: number; tagVectorMissing: number; reviewTextWrites24h: number;
  lastFunnelEventAt: string | null; lastImpressionAt: string | null;
}
export interface CollectionFlag { name: string; enabled: boolean }
export interface CollectionWarning { code: string; batchType: string | null }
export interface Collection {
  jobs: CollectionJob[]; data: CollectionData; flags: CollectionFlag[]; warnings: CollectionWarning[];
}
export interface Status {
  server: StatusServer;
  recentEvents: StatusEvent[];
  errors: StatusErrors;
  collection: Collection;
}
