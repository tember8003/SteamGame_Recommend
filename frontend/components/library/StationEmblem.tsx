import styles from './station-emblem.module.css';

export type StationKind = 'warehouse' | 'gallery' | 'attributes' | 'map';

/** Small inventory illustrations, drawn on the same pixel grid as the village. */
export default function StationEmblem({ kind }: { kind: StationKind }) {
  return <svg className={styles.emblem} viewBox="0 0 48 48" aria-hidden="true" focusable="false" shapeRendering="crispEdges">
    {kind === 'warehouse' ? <>
      <path className={styles.outline} d="M8 12h32v4h4v24h-4v4H8v-4H4V16h4z" />
      <path className={styles.wood} d="M8 16h32v22H8z" />
      <path className={styles.shadow} d="M8 28h32v10H8zM14 16h4v22h-4zm16 0h4v22h-4z" />
      <path className={styles.gold} d="M8 14h32v4H8zm0 12h32v4H8zm0 12h32v4H8z" />
      <path className={styles.light} d="M10 14h28v2H10zm0 12h10v2H10zm18 0h10v2H28z" />
      <path className={styles.outline} d="M19 24h10v12H19z" /><path className={styles.gold} d="M21 26h6v8h-6z" /><path className={styles.outline} d="M23 28h2v4h-2z" />
      <path className={styles.light} d="M8 18h2v6H8zm30 12h2v6h-2z" />
    </> : null}
    {kind === 'gallery' ? <>
      <path className={styles.outline} d="M6 4h36v34H6zm10 34h16v4H16zm-6 4h28v4H10z" />
      <path className={styles.gold} d="M8 6h32v30H8z" /><path className={styles.light} d="M8 6h32v2H8zm0 2h2v26H8z" />
      <path className={styles.shadow} d="M12 10h24v22H12z" /><path className={styles.ink} d="M14 12h20v18H14z" />
      <path className={styles.gold} d="M20 14h8v10h-2v2h-4v-2h-2zm-4 2h4v2h-2v4h2v2h-4zm12 0h4v8h-4v-2h2v-4h-2zm-6 10h4v2h4v2H18v-2h4z" />
      <path className={styles.light} d="M22 14h2v8h-2z" /><path className={styles.wood} d="M20 38h8v4h-8zm-6 4h20v2H14z" />
    </> : null}
    {kind === 'attributes' ? <>
      <path className={styles.outline} d="M8 4h26v4h6v36H8z" /><path className={styles.paper} d="M12 8h20v6h4v26H12z" />
      <path className={styles.gold} d="M12 8h4v32h-4zM30 8h4v6h-4zm4 6h2v4h-2z" />
      <path className={styles.shadow} d="M18 14h10v2H18zm0 6h14v2H18zm0 7h14v2H18zm0 7h14v2H18z" />
      <path className={styles.teal} d="M18 20h10v2H18zm0 7h6v2h-6zm0 7h12v2H18z" />
      <path className={styles.outline} d="M29 32h12v10h-4v4h-4v-4h-4z" /><path className={styles.gold} d="M31 34h8v6h-2v2h-4v-2h-2z" /><path className={styles.light} d="M33 34h2v4h-2z" />
    </> : null}
    {kind === 'map' ? <>
      <path className={styles.outline} d="M2 10 16 4l16 6 14-6v34l-14 6-16-6-14 6z" />
      <path className={styles.paper} d="m6 12 10-4 16 6 10-4v26l-10 4-16-6-10 4z" />
      <path className={styles.gold} d="M14 10h4v24h-4zm16 4h4v24h-4z" />
      <path className={styles.teal} d="M8 28h4v2H8zm4-4h4v2h-4zm4-4h6v2h-6zm6 2h4v2h-4zm4 4h4v2h-4zm4-6h4v4h-4z" />
      <path className={styles.shadow} d="M8 16h4v4H8zm14 10h4v6h-4z" /><path className={styles.red} d="M34 14h2v2h4v4h-4v8h-2z" />
      <path className={styles.light} d="M36 16h2v2h-2zM6 12h2v22H6z" />
    </> : null}
  </svg>;
}
