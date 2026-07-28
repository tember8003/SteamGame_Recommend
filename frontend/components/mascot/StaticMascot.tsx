import styles from "./StaticMascot.module.css";

export type MascotState = "idle" | "thinking" | "speaking";

// v0 정적 마스코트 자리표시자, 실제 아트 도착 시 이미지로 교체함, reduced-motion 시 애니메이션 끔
export default function StaticMascot({ state = "idle" }: { state?: MascotState }) {
  return (
    <div className={styles.wrap} data-state={state} aria-hidden="true">
      <div className={styles.orb} />
      <span className={styles.spark}>✦</span>
    </div>
  );
}
