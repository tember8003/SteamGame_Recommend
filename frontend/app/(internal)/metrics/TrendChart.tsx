import styles from './dashboard.module.css';
import { num, shortDate } from './format';
import type { Daily } from './types';

// dataviz 카테고리 팔레트의 1,2,3번 슬롯(어두운 배경용 단계)이다
const SERIES: { key: keyof Omit<Daily, 'date'>; label: string; color: string }[] = [
  { key: 'visitors', label: '방문자', color: '#3987e5' },
  { key: 'testCompletes', label: '테스트 완료', color: '#d95926' },
  { key: 'libraryCompletes', label: '분석 완료', color: '#199e70' },
];
const W = 640, H = 200, PAD = 28;
// 점이 선을 가로지를 때도 구분되게 카드 표면 색으로 테를 두른다
const SURFACE_RING = '#1a1a19';

// 날짜마다 점을 찍어 선으로 잇는다, 하루뿐이면 점만 보인다
export default function TrendChart({ days }: { days: Daily[] }) {
  const max = Math.max(1, ...days.flatMap(d => SERIES.map(s => d[s.key])));
  const x = (i: number) => PAD + (days.length <= 1 ? (W - 2 * PAD) / 2 : (i * (W - 2 * PAD)) / (days.length - 1));
  const y = (v: number) => H - PAD - (v / max) * (H - 2 * PAD);
  return (
    <figure className={styles.trend}>
      {/* 세로축 최댓값과 날짜 눈금은 SVG 밖 HTML로 둬서 390px 에서도 글자가 그림 치수에 밀려 깨지지 않게 한다 */}
      <div className={styles.trendChartWrap}>
        <span className={styles.trendMax}>최대 {num(max)}</span>
        <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="하루별 추이">
          <line x1={PAD} y1={H - PAD} x2={W - PAD} y2={H - PAD} className={styles.axis} />
          {SERIES.map(s => (
            <g key={s.key}>
              <polyline fill="none" stroke={s.color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round"
                points={days.map((d, i) => `${x(i)},${y(d[s.key])}`).join(' ')} />
              {days.map((d, i) => (
                <circle key={d.date} cx={x(i)} cy={y(d[s.key])} r={4} fill={s.color} stroke={SURFACE_RING} strokeWidth={2}>
                  <title>{`${d.date} ${s.label} ${num(d[s.key])}`}</title>
                </circle>
              ))}
            </g>
          ))}
        </svg>
      </div>
      <div className={styles.trendDates}>
        <span>{days[0] ? shortDate(days[0].date) : ''}</span>
        <span>{days.length ? shortDate(days[days.length - 1].date) : ''}</span>
      </div>
      <figcaption className={styles.legend}>
        {SERIES.map(s => (
          <span key={s.key}><i style={{ background: s.color }} />{s.label}</span>
        ))}
      </figcaption>
    </figure>
  );
}
