import styles from "./VillageNpc.module.css";

type Npc = "fortune" | "explorer" | "archive";

// 같은 원본의 고정 영역과 머리 영역을 나누어 간판이나 가구는 움직이지 않게 한다.
export default function VillageNpc({ npc }: { npc: Npc }) {
  const src = `/images/village/${npc}.webp`;
  return (
    <span className={`${styles.scene} ${styles[npc]}`} aria-hidden="true">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className={`${styles.image} ${styles.base}`} src={src} width="512" height="384" alt="" draggable={false} />
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className={`${styles.image} ${styles.head}`} src={src} width="512" height="384" alt="" draggable={false} />
    </span>
  );
}
