import Link from "next/link";
import styles from "./Header.module.css";
import Button from "./ui/Button";

// 상단 브랜드 바, v0 은 Login/Signup·언어 토글이 비활성 자리표시자(연결은 v0.5)
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
        <button className={styles.iconBtn} aria-label="Language" disabled>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
            <circle cx="12" cy="12" r="9" />
            <path d="M3 12h18M12 3c3 3.5 3 14.5 0 18M12 3c-3 3.5-3 14.5 0 18" />
          </svg>
        </button>
        <Button variant="glass" disabled>Log in</Button>
        <Button variant="violet" disabled>Sign up</Button>
      </div>
    </header>
  );
}
