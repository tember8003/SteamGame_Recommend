import styles from './dashboard.module.css';
import { num } from './format';

interface Item { label: string; value: number; note?: string; code?: string }

// 가장 큰 값을 100% 로 두고 막대 길이를 정한다, 값이 모두 0 이면 막대를 그리지 않는다
// title 을 주면 목록 위에 소제목을 붙인다, code 를 주면 화면에는 label(이름)을 보여 주고 title 속성에 원래 코드를 둔다
export default function BarList({ items, ariaLabel, title }: { items: Item[]; ariaLabel: string; title?: string }) {
  const max = Math.max(0, ...items.map(i => i.value));
  return (
    <div className={styles.barGroup}>
      {title ? <h4 className={styles.barGroupTitle}>{title}</h4> : null}
      <ul className={styles.barList} aria-label={ariaLabel}>
        {items.map(i => (
          <li key={i.code ?? i.label} className={styles.barRow}>
            <span className={styles.barLabel} title={i.code ?? i.label}>{i.label}</span>
            <span className={styles.barTrack}>
              <span className={styles.barFill} style={{ width: max > 0 ? `${(i.value / max) * 100}%` : 0 }} />
            </span>
            <span className={styles.barValue}>{num(i.value)}{i.note ? <small> {i.note}</small> : null}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
