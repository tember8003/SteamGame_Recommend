import { Fragment } from 'react';
import styles from './dashboard.module.css';
import {
  eventTypeLabel, FEATURE_LABEL, formatKst, formatKstShort, num,
  shortRevision, verdictCodeTitle, verdictLabel,
} from './format';
import type { Status, StatusEvent } from './types';

// 오류 코드는 공백 없는 긴 토큰이라 밑줄 뒤에 <wbr> 을 넣어 그 자리에서만 줄이 바뀌게 한다
function codeWithBreaks(code: string) {
  const parts = code.split('_');
  return parts.map((part, i) => {
    const isLast = i === parts.length - 1;
    return (
      <Fragment key={i}>
        {part}{isLast ? '' : '_'}
        {isLast ? null : <wbr />}
      </Fragment>
    );
  });
}

// ref 가 FEATURE_SELECT 의 기능 코드이면 한글 이름을 보여 주고 원래 코드는 title 에 둔다
function refCell(e: StatusEvent) {
  if (e.type === 'FEATURE_SELECT' && e.ref) {
    return { text: FEATURE_LABEL[e.ref] ?? e.ref, title: e.ref };
  }
  return { text: e.ref ?? '', title: undefined };
}

// 대시보드 조회와 따로 불러오는 운영 상태 영역이다, 토큰/401 처리는 page.tsx 의 공통 흐름을 그대로 쓴다
// 실패해도 대시보드는 그대로 두고 이 영역에만 오류 문구를 보인다
export default function StatusPanel({
  status, error, loading, onRefresh,
}: { status: Status | null; error: string | null; loading: boolean; onRefresh: () => void }) {
  // 최근 목록이 비었을 때의 안내, 횟수도 비었으면 오류가 없는 것이고 횟수만 있으면 경로 없음(404)만 쌓인 것임
  const noRecentNote = status && status.errors.counts.length === 0
    ? '기동 이후 오류 없음'
    : '최근 목록 없음, 경로 없음(404)은 횟수만 셉니다';
  return (
    <section aria-label="운영 상태">
      <div className={styles.statusHeader}>
        <h2>운영 상태</h2>
        <button type="button" className={styles.ghost} onClick={onRefresh} disabled={loading}>
          {loading ? '불러오는 중' : '새로고침'}
        </button>
      </div>

      {error ? <p role="alert" className={styles.error}>{error}</p> : null}
      {!status && loading ? <p className={styles.note}>불러오는 중</p> : null}

      {status ? (
        <>
          <div className={styles.two}>
            <div className={styles.card} aria-label="서버 상태">
              <h3>서버 상태</h3>
              <dl className={styles.statusList}>
                <div><dt>백엔드 커밋</dt><dd title={status.server.revision}>{shortRevision(status.server.revision)}</dd></div>
                <div><dt>기동 시각</dt><dd>{formatKst(status.server.startedAt)}</dd></div>
                <div><dt>Flyway 버전</dt><dd>{status.server.flywayVersion ?? '-'}</dd></div>
              </dl>
            </div>

            <div className={styles.card} aria-label="오류 코드별 횟수">
              <h3>오류 코드별 횟수</h3>
              <p className={styles.note}>기동 이후({formatKst(status.errors.since)})</p>
              {status.errors.counts.length > 0 ? (
                <table className={`${styles.table} ${styles.textTable}`}>
                  <thead><tr><th>상태</th><th>코드</th><th>횟수</th></tr></thead>
                  <tbody>
                    {status.errors.counts.map((c, i) => (
                      <tr key={i}>
                        <td className={styles.nowrap}>{c.status}</td>
                        <td>{codeWithBreaks(c.errorCode)}</td>
                        <td>{num(c.count)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : null}
            </div>
          </div>

          <section className={styles.card} aria-label="최근 오류">
            <h3>최근 오류</h3>
            {status.errors.recent.length === 0 ? (
              <p className={styles.note}>{noRecentNote}</p>
            ) : (
              <table className={`${styles.table} ${styles.textTable}`}>
                <thead><tr><th>시간</th><th>상태</th><th>코드</th><th>경로</th></tr></thead>
                <tbody>
                  {status.errors.recent.map((e, i) => (
                    <tr key={i}>
                      <td className={styles.nowrap}>{formatKstShort(e.at)}</td>
                      <td className={styles.nowrap}>{e.status}</td>
                      <td>{codeWithBreaks(e.errorCode)}</td>
                      <td className={styles.wrap}>{e.route}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>

          <section className={styles.card} aria-label="최근 이벤트">
            <h3>최근 이벤트</h3>
            {status.recentEvents.length === 0 ? (
              <p className={styles.note}>이벤트 없음</p>
            ) : (
              <table className={`${styles.table} ${styles.textTable}`}>
                <thead><tr><th>시간</th><th>종류</th><th>ref</th><th>판정</th></tr></thead>
                <tbody>
                  {status.recentEvents.map((e, i) => {
                    const ref = refCell(e);
                    return (
                      <tr key={i}>
                        <td className={styles.nowrap}>{formatKstShort(e.at)}</td>
                        <td className={styles.nowrap}>{eventTypeLabel(e.type, e.channel)}</td>
                        <td className={styles.wrap} title={ref.title}>{ref.text}</td>
                        <td title={verdictCodeTitle(e) || undefined}>{verdictLabel(e)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </section>
        </>
      ) : null}
    </section>
  );
}
