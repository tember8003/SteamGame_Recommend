import styles from './dashboard.module.css';
import { num } from './format';
import type { CodeCount } from './types';

const ROWS = ['EN', 'EM', 'GN', 'GM'];
const COLS = ['HS', 'HP', 'CS', 'CP'];

// 시퀀셜 블루, 어두운 카드 표면 위에서 쓰는 카테고리 1번과 같은 색이다
const FILL = { r: 57, g: 135, b: 229 };
// 0칸 바탕색(그리드라인)과 같다, 섞기 시작점을 0칸과 똑같이 맞춰 적게 나온 칸이 0칸보다 어두워지지 않게 한다
const ZERO_BG = { r: 44, g: 44, b: 42 };

// 칸 바탕을 섞은 색의 밝기(sRGB 근사)를 보고 글자를 흰색과 잉크색 중에 고른다
function cellStyle(n: number, max: number): { background?: string; color?: string } {
  if (n <= 0 || max <= 0) return {};
  const alpha = 0.12 + 0.88 * (n / max);
  const r = Math.round(ZERO_BG.r + alpha * (FILL.r - ZERO_BG.r));
  const g = Math.round(ZERO_BG.g + alpha * (FILL.g - ZERO_BG.g));
  const b = Math.round(ZERO_BG.b + alpha * (FILL.b - ZERO_BG.b));
  const luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
  return {
    background: `rgb(${r}, ${g}, ${b})`,
    color: luminance >= 0.45 ? '#0b0b0b' : '#ffffff',
  };
}

// 많이 나온 유형일수록 진하게 칠한다, 0 회는 빈 칸으로 둔다
export default function TypeGrid({ counts }: { counts: CodeCount[] }) {
  const byCode = new Map(counts.map(c => [c.code, c.count]));
  const max = Math.max(0, ...counts.map(c => c.count));
  return (
    <table className={styles.typeGrid} aria-label="취향 유형 분포">
      <tbody>
        {ROWS.map(r => (
          <tr key={r}>
            {COLS.map(c => {
              const code = `${r[0]}${r[1]}${c[0]}${c[1]}`;
              const n = byCode.get(code) ?? 0;
              return (
                <td key={code} style={cellStyle(n, max)}>
                  <span className={styles.typeCode}>{code}</span>
                  <span className={styles.typeCount}>{num(n)}</span>
                </td>
              );
            })}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
