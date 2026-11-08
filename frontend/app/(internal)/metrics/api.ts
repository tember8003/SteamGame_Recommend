import { apiUrl } from '@/lib/api';
import type { Dashboard, Range, Status } from './types';

// 토큰이 없거나 틀려 401 이 온 경우를 나타냄, 대시보드와 운영 상태 조회 둘 다 같은 토큰을 쓰므로 함께 쓴다
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

// 기간과 무관한 지금 상태 조회, 대시보드 조회와 따로 부른다
export async function fetchStatus(token: string): Promise<Status> {
  const res = await fetch(apiUrl('/api/metrics/status'), {
    headers: { 'X-Metrics-Token': token },
    cache: 'no-store',
  });
  if (res.status === 401) throw new DashboardAuthError('unauthorized');
  if (!res.ok) throw new Error(`GET /api/metrics/status failed: ${res.status}`);
  return res.json() as Promise<Status>;
}
