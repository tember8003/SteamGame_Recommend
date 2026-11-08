import { apiUrl } from '@/lib/api';
import type { Dashboard, Range } from './types';

// 토큰이 없거나 틀려 401 이 온 경우를 나타냄
export class DashboardAuthError extends Error {}

export async function fetchDashboard(token: string, range: Range): Promise<Dashboard> {
  const res = await fetch(apiUrl(`/api/metrics/dashboard?range=${range}`), {
    headers: { 'X-Metrics-Token': token },
    cache: 'no-store',
  });
  if (res.status === 401) throw new DashboardAuthError('unauthorized');
  if (!res.ok) throw new Error(`GET /api/metrics/dashboard failed: ${res.status}`);
  return res.json() as Promise<Dashboard>;
}
