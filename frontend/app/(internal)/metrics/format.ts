import { genreName } from '@/lib/library/base';

// 숫자는 천 단위 쉼표, 비율은 소수 없는 퍼센트로 표기한다
export const num = (n: number) => n.toLocaleString('ko-KR');
export const pct = (part: number, whole: number) => (whole > 0 ? `${Math.round((part / whole) * 100)}%` : '-');
export const ratio = (r: number | null) => (r === null ? '-' : `${Math.round(r * 100)}%`);
// 2026-11-08 을 11-08 로 줄인다
export const shortDate = (iso: string) => iso.slice(5);

export const STEP_LABEL: Record<string, string> = {
  LANDING_VISIT: '방문', FEATURE_SELECT: '기능 선택', TEST_START: '테스트 시작',
  TEST_COMPLETE: '테스트 완료', LIBRARY_COMPLETE: '분석 완료', SHARE_CLICK: '공유 클릭',
};
export const FEATURE_LABEL: Record<string, string> = { TASTE: '취향 점술소', SIMILAR: '게임 탐색소', LIBRARY: '플레이 기록관' };

// 라이브러리 판정 장부(소비 성향) 이름표, 모르는 코드는 호출부가 코드 그대로 보여 준다
export const LEDGER_LABEL: Record<string, string> = {
  STARTER: '새내기', LEGENDARY_COLLECTOR: '전설의 수집가', COLLECTOR: '수집가',
  EXPLORER: '맛보기 탐험가', PRACTICAL: '실속파', BALANCED: '균형형',
};
// 라이브러리 판결문(죄목) 이름표, 모르는 코드는 호출부가 코드 그대로 보여 준다
export const CRIME_LABEL: Record<string, string> = {
  PILE_OF_SHAME: '쌓아두기', ONE_GAME_DEVOTION: '한 게임 올인', GENRE_LOYALIST: '한 장르 고집',
  GENRE_NOMAD: '장르 방랑', INNOCENT: '무죄',
};
// 추천 반응 표의 경로(피드백이 발생한 요청 종류) 이름표, 모르는 코드는 호출부가 코드 그대로 보여 준다
export const CONTEXT_LABEL: Record<string, string> = {
  INPUT: '직접 입력', PROFILE: '프로필', RECENT: '최근 플레이', SIMILAR: '비슷한 게임',
  RANDOM: '무작위', MATCH: '매칭', TASTE: '취향 테스트',
};

// 장르 거점 코드를 한글 이름으로 바꾼다, lib/library/base 의 genreName 이 가운뎃점(U+00B7)으로 묶어 주는 걸 쉼표로 다시 푼다
export const libraryGenreLabel = (code: string) =>
  code === 'INSUFFICIENT' ? '기록 부족' : genreName(code, 'ko').replace(/·/g, ', ');
