import Link from "next/link";
import styles from "./Header.module.css";
import HeaderActions from "./HeaderActions";

// 상단 브랜드 바, 언어 토글은 연결됨, Login/Signup 은 아직 비활성 자리표시자
export default function Header() {
  return (
    <header className={styles.header}>
      <Link href="/" className={styles.brand} aria-label="PLAYLOOM home">
        <svg className={styles.logo} viewBox="0 0 32 32" fill="none" aria-hidden="true">
          <defs>
            <linearGradient id="ploom" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0" stopColor="#6d5efc" />
              <stop offset="1" stopColor="#3aa0ff" />
            </linearGradient>
          </defs>
          <circle cx="16" cy="16" r="7" fill="url(#ploom)" />
          <ellipse
            cx="16"
            cy="16"
            rx="14"
            ry="5"
            transform="rotate(-20 16 16)"
            stroke="#ffffff"
            strokeWidth="1.5"
            opacity="0.85"
          />
        </svg>
        <span className={styles.wordmark}>PLAYLOOM</span>
      </Link>

      <div className={styles.actions}>
        <HeaderActions />
      </div>
    </header>
  );
}
