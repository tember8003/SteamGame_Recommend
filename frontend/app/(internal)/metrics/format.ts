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
  SHARE_INFLOW: '공유 유입',
};
export const FEATURE_LABEL: Record<string, string> = { TASTE: '취향 점술소', SIMILAR: '게임 탐색소', LIBRARY: '플레이 기록관' };

// 운영 상태 최근 이벤트 표의 종류 칸, 채널은 SHARE_CLICK 에만 있어 있을 때만 괄호로 붙인다
export const eventTypeLabel = (type: string, channel: string | null) => {
  const label = STEP_LABEL[type] ?? type;
  return channel ? `${label}(${channel})` : label;
};

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
  code === 'INSUFFICIENT' ? '기록 부족' : genreName(code, 'ko').replace(/\u00B7/g, ', ');

// 커밋은 앞 7자리만 보여 준다, unknown 은 그대로 둔다
export const shortRevision = (revision: string) => (revision === 'unknown' ? 'unknown' : revision.slice(0, 7));

// 운영 상태 화면의 시각은 모두 한국 시간으로 고정한다, 실행 환경의 시간대와 무관하게 timeZone 을 지정한다
// 포매터를 모듈에서 한 번만 만들어 두고 매번 재사용한다, en-CA 는 formatToParts 에서 각 칸을 바로 꺼내 쓰기 좋은 포맷이다
const kstFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
});

// 잘못된 날짜(isNaN)는 null 을 돌려주고, 호출부가 '-' 로 보여 준다
const kstParts = (iso: string) => {
  const parsed = new Date(iso);
  if (isNaN(parsed.getTime())) return null;
  const parts = kstFormatter.formatToParts(parsed);
  const take = (type: string) => parts.find(p => p.type === type)?.value ?? '00';
  return { y: take('year'), m: take('month'), d: take('day'), h: take('hour'), mi: take('minute') };
};

// 기동 시각처럼 전체 날짜가 필요할 때 쓴다(예: "2026-11-08 14:47"), 잘못된 날짜는 '-' 로 보여 준다
export const formatKst = (iso: string) => {
  const p = kstParts(iso);
  return p ? `${p.y}-${p.m}-${p.d} ${p.h}:${p.mi}` : '-';
};

// 표 칸처럼 짧게 "MM-DD HH:mm" 로 쓴다, 잘못된 날짜는 '-' 로 보여 준다
export const formatKstShort = (iso: string) => {
  const p = kstParts(iso);
  return p ? `${p.m}-${p.d} ${p.h}:${p.mi}` : '-';
};

interface Verdictish { ledgerCode: string | null; crimeCode: string | null; genreCode: string | null }

// 최근 이벤트 표의 판정 칸, 장부/판결문/장르 이름을 쉼표로 이어 쓰고 전부 없으면 빈 문자열이다
export const verdictLabel = (e: Verdictish) =>
  [
    e.ledgerCode ? (LEDGER_LABEL[e.ledgerCode] ?? e.ledgerCode) : null,
    e.crimeCode ? (CRIME_LABEL[e.crimeCode] ?? e.crimeCode) : null,
    e.genreCode ? libraryGenreLabel(e.genreCode) : null,
  ].filter((v): v is string => v !== null).join(', ');

// verdictLabel 과 짝을 이루는 title 속성용, 원래 코드를 그대로 쉼표로 이어 쓴다
export const verdictCodeTitle = (e: Verdictish) =>
  [e.ledgerCode, e.crimeCode, e.genreCode].filter((v): v is string => v !== null).join(', ');
