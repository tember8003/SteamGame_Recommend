import { describe, expect, it } from 'vitest';
import { formatKst, formatKstShort, jobLabel, jobState, libraryGenreLabel, truncate, warningText } from './format';
import type { CollectionJob } from './types';

describe('libraryGenreLabel', () => {
  it('replaces the middle dot from genreName with a comma', () => {
    const label = libraryGenreLabel('ACTION_SHOOTER');
    expect(label).toBe('액션, 슈팅');
    expect(label).not.toContain('\u00B7');
  });

  it('shows INSUFFICIENT as a Korean phrase', () => {
    expect(libraryGenreLabel('INSUFFICIENT')).toBe('기록 부족');
  });
});

// 테스트 실행 시간대는 vitest.config.ts 에서 UTC 로 고정한다, 그래야 timeZone 옵션을 빼먹어도
// 우연히 통과하는(이 기기의 Node 기본 시간대가 Asia/Seoul 이라) 상황이 생기지 않는다
describe('formatKst / formatKstShort', () => {
  it('converts a UTC instant to KST', () => {
    expect(formatKstShort('2026-11-08T05:47:22Z')).toBe('11-08 14:47');
    expect(formatKst('2026-11-08T05:47:22Z')).toBe('2026-11-08 14:47');
  });

  it('crosses midnight into the next day at the KST boundary', () => {
    expect(formatKstShort('2026-11-08T15:00:00Z')).toBe('11-09 00:00');
  });

  it('stays on the same day just before the KST boundary', () => {
    expect(formatKstShort('2026-11-08T14:59:00Z')).toBe('11-08 23:59');
  });

  it("shows '-' for an invalid date", () => {
    expect(formatKst('not-a-date')).toBe('-');
    expect(formatKstShort('not-a-date')).toBe('-');
  });
});

const baseJob: CollectionJob = {
  batchType: 'steam_catalog', lastStartedAt: '2026-11-20T05:30:00Z', lastFinishedAt: '2026-11-20T06:00:00Z',
  lastProcessed: 39958, lastFailed: 0, lastNote: 'pages=141', lastRunFailed: false, running: false, stuck: false,
  lastSuccessAt: '2026-11-20T06:00:00Z', lastSuccessProcessed: 39958, expectedIntervalHours: 26,
  stale: false, runs7d: 7, failedRuns7d: 0,
};

describe('jobLabel', () => {
  it('names each batch type in Korean and keeps unknown types as they are', () => {
    expect(jobLabel('steam_catalog')).toBe('카탈로그 수집');
    expect(jobLabel('tag_cooccurrence')).toBe('태그 쌍');
    expect(jobLabel('detail_rolling')).toBe('상세 수집');
    expect(jobLabel('embed_backfill')).toBe('임베딩');
    expect(jobLabel('tag_embedding')).toBe('GNN 학습');
    expect(jobLabel('something_new')).toBe('something_new');
  });
});

// 경고와 같은 순서로 판단한다, 기록 없음, 멈춤 추정, 실패, 주기 초과, 실행 중, 정상
describe('jobState', () => {
  it('is 정상 for a finished healthy run', () => {
    expect(jobState(baseJob)).toBe('정상');
  });

  it('is 기록 없음 when the job never ran', () => {
    expect(jobState({ ...baseJob, lastStartedAt: null, stale: true })).toBe('기록 없음');
  });

  it('puts a stuck run ahead of failure', () => {
    expect(jobState({ ...baseJob, stuck: true, lastRunFailed: true, stale: true, lastFinishedAt: null })).toBe('멈춤 추정');
  });

  it('is 기록 없음 before anything else', () => {
    expect(jobState({ ...baseJob, lastStartedAt: null, stuck: true })).toBe('기록 없음');
  });

  it('puts failure ahead of staleness and running', () => {
    expect(jobState({ ...baseJob, lastRunFailed: true, stale: true, running: true })).toBe('실패');
  });

  it('puts staleness ahead of running so a dead process does not look busy', () => {
    expect(jobState({ ...baseJob, stale: true, running: true })).toBe('주기 초과');
  });

  it('is 실행 중 while a fresh run is in progress', () => {
    expect(jobState({ ...baseJob, running: true, lastFinishedAt: null })).toBe('실행 중');
  });
});

describe('warningText', () => {
  it('describes each warning code in Korean', () => {
    expect(warningText({ code: 'JOB_STALE', batchType: 'embed_backfill' })).toBe('임베딩 주기 초과');
    expect(warningText({ code: 'JOB_STUCK', batchType: 'tag_embedding' }))
      .toBe('GNN 학습 24시간 넘게 끝나지 않음 (멈춤 추정)');
    expect(warningText({ code: 'JOB_FAILED', batchType: 'detail_rolling' })).toBe('상세 수집 마지막 실행 실패');
    expect(warningText({ code: 'CATALOG_DROP', batchType: 'steam_catalog' })).toBe('카탈로그 저장 수 5% 넘게 감소');
    expect(warningText({ code: 'DETAIL_NO_WRITES', batchType: 'detail_rolling' }))
      .toBe('상세 수집이 돌았는데 24시간 동안 저장된 리뷰 본문 0');
  });

  it('falls back to the raw code for an unknown warning', () => {
    expect(warningText({ code: 'NEW_CODE', batchType: null })).toBe('NEW_CODE');
    expect(warningText({ code: 'NEW_CODE', batchType: 'tag_embedding' })).toBe('NEW_CODE(GNN 학습)');
  });
});

describe('truncate', () => {
  it('keeps short text and cuts long text with an ellipsis', () => {
    expect(truncate('pairs=74389', 10)).toBe('pairs=7438…');
    expect(truncate('pairs=1', 10)).toBe('pairs=1');
    expect(truncate('0123456789', 10)).toBe('0123456789');
  });
});
