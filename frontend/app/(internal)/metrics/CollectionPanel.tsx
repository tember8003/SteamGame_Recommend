import styles from './dashboard.module.css';
import { FLAG_LABEL, formatKst, formatKstShort, jobLabel, jobState, num, truncate, warningText } from './format';
import type { JobState } from './format';
import type { Collection, CollectionJob } from './types';

// note 는 표 칸이 넘치지 않을 만큼만 보여 주고 전체는 title 에 둔다
const NOTE_MAX = 40;

const STATE_CLASS: Record<JobState, string> = {
  '정상': styles.badge,
  '실행 중': `${styles.badge} ${styles.badgeRun}`,
  '실패': `${styles.badge} ${styles.badgeBad}`,
  '주기 초과': `${styles.badge} ${styles.badgeBad}`,
  '기록 없음': `${styles.badge} ${styles.badgeIdle}`,
  '멈춤 추정': `${styles.badge} ${styles.badgeBad}`,
};

const kstOrDash = (iso: string | null) => (iso ? formatKstShort(iso) : '-');

// 실행 중이거나 멈춘 실행은 처리 수가 아직 0 이라 숫자 대신 비워 둔다
function resultCell(j: CollectionJob) {
  if (j.running || j.stuck || j.lastProcessed === null || j.lastFailed === null) return '-';
  return `${num(j.lastProcessed)} / ${num(j.lastFailed)}`;
}

function successCell(j: CollectionJob) {
  if (!j.lastSuccessAt) return '-';
  const at = formatKstShort(j.lastSuccessAt);
  return j.lastSuccessProcessed === null ? at : `${at}, ${num(j.lastSuccessProcessed)}건`;
}

// 작업이 남긴 기록과 실제 DB 상태를 함께 보여 주는 수집 영역이다, 작업의 자기 보고만으로는 저장이 멈춘 것을 놓친 적이 있음
export default function CollectionPanel({ collection }: { collection: Collection }) {
  const { jobs, data, flags, warnings } = collection;
  return (
    <section aria-label="수집">
      <section className={styles.card} aria-label="수집 경고">
        <h3>수집 경고</h3>
        {warnings.length === 0 ? (
          <p className={styles.note}>경고 없음</p>
        ) : (
          <ul className={styles.warnings}>
            {warnings.map((w, i) => <li key={i}>{warningText(w)}</li>)}
          </ul>
        )}
      </section>

      <section className={styles.card} aria-label="배치 작업">
        <h3>배치 작업</h3>
        <p className={styles.note}>작업이 남긴 기록이다, 실제로 들어온 양은 아래 실제 데이터와 견준다</p>
        <table className={`${styles.table} ${styles.textTable}`}>
          <thead>
            <tr><th>작업</th><th>마지막 실행</th><th>결과(처리/실패)</th><th>note</th><th>마지막 성공</th><th>상태</th></tr>
          </thead>
          <tbody>
            {jobs.map(j => {
              const state = jobState(j);
              return (
                <tr key={j.batchType}>
                  <td className={styles.nowrap} title={j.batchType}>{jobLabel(j.batchType)}</td>
                  <td className={styles.nowrap}>{kstOrDash(j.lastStartedAt)}</td>
                  <td className={styles.nowrap}>{resultCell(j)}</td>
                  <td className={styles.wrap} title={j.lastNote ?? undefined}>{j.lastNote ? truncate(j.lastNote, NOTE_MAX) : '-'}</td>
                  <td className={styles.nowrap}>{successCell(j)}</td>
                  <td className={styles.nowrap}>
                    <span className={STATE_CLASS[state]} title={`7일 실행 ${j.runs7d}회, 실패 ${j.failedRuns7d}회`}>{state}</span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </section>

      <div className={styles.two}>
        <div className={styles.card} aria-label="실제 데이터">
          <h3>실제 데이터</h3>
          <dl className={styles.statusList}>
            <div><dt>게임 수</dt><dd>{num(data.games)}</dd></div>
            <div><dt>임베딩 빈 게임</dt><dd>{num(data.embeddingMissing)}</dd></div>
            <div><dt>GNN 벡터 없는 게임</dt><dd>{num(data.tagVectorMissing)}</dd></div>
            <div><dt>24시간 리뷰 본문 저장</dt><dd>{num(data.reviewTextWrites24h)}</dd></div>
            <div><dt>마지막 퍼널 이벤트</dt><dd>{data.lastFunnelEventAt ? formatKst(data.lastFunnelEventAt) : '-'}</dd></div>
            <div><dt>마지막 추천 노출</dt><dd>{data.lastImpressionAt ? formatKst(data.lastImpressionAt) : '-'}</dd></div>
          </dl>
        </div>

        <div className={styles.card} aria-label="기능 설정">
          <h3>기능 설정</h3>
          <dl className={styles.statusList}>
            {flags.map(f => (
              <div key={f.name}><dt>{FLAG_LABEL[f.name] ?? f.name}</dt><dd>{f.enabled ? '켜짐' : '꺼짐'}</dd></div>
            ))}
          </dl>
        </div>
      </div>
    </section>
  );
}
