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
