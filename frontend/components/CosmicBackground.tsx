import styles from "./CosmicBackground.module.css";

// 우주 배경, 장식 전용이라 aria-hidden, reduced-motion 시 별 반짝임·유성 끔
// 별자리(점+선)는 유사도 그래프 모티프와 연결됨
export default function CosmicBackground() {
  return (
    <div className={styles.sky} aria-hidden="true">
      <div className={styles.stars} />

      <svg
        className={styles.scene}
        viewBox="0 0 1440 900"
        preserveAspectRatio="xMidYMid slice"
      >
        <defs>
          <radialGradient id="pl-moon-glow" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="rgba(220,224,240,0.26)" />
            <stop offset="45%" stopColor="rgba(180,190,230,0.09)" />
            <stop offset="100%" stopColor="rgba(180,190,230,0)" />
          </radialGradient>
          <radialGradient id="pl-moon-body" cx="40%" cy="38%" r="65%">
            <stop offset="0%" stopColor="#eef0f6" />
            <stop offset="100%" stopColor="#c7cbdc" />
          </radialGradient>
        </defs>

        {/* 달 */}
        <circle cx="1180" cy="230" r="140" fill="url(#pl-moon-glow)" />
        <circle cx="1180" cy="230" r="50" fill="url(#pl-moon-body)" />
        <circle cx="1163" cy="214" r="8" fill="#0a0e1a" opacity="0.06" />
        <circle cx="1199" cy="242" r="6" fill="#0a0e1a" opacity="0.05" />
        <circle cx="1174" cy="252" r="5" fill="#0a0e1a" opacity="0.05" />

        {/* 별자리 (그래프 모티프) */}
        <path
          d="M150 170 L250 140 L330 210 L250 270 L150 240 Z M250 270 L340 300"
          stroke="rgba(120,160,240,0.28)"
          strokeWidth="1"
          fill="none"
        />
        <g fill="rgba(170,195,255,0.9)">
          <circle cx="150" cy="170" r="3.5" />
          <circle cx="250" cy="140" r="3" />
          <circle cx="330" cy="210" r="3.5" />
          <circle cx="250" cy="270" r="3" />
          <circle cx="150" cy="240" r="3" />
          <circle cx="340" cy="300" r="2.5" />
        </g>
      </svg>

      <span className={`${styles.meteor} ${styles.m1}`} />
      <span className={`${styles.meteor} ${styles.m2}`} />
      <span className={`${styles.meteor} ${styles.m3}`} />
      <span className={`${styles.meteor} ${styles.m4}`} />
    </div>
  );
}
