"use client";

import Link from "next/link";
import styles from "./Header.module.css";
import HeaderTagline from "./HeaderTagline";
import LanguageToggle from "./LanguageToggle";
import { useLocale } from "@/lib/i18n/LocaleProvider";
import { localeHref } from "@/lib/i18n/routing";

// 상단 브랜드 바, 왼쪽은 로고와 부제, 오른쪽은 언어 토글
export default function Header() {
  const locale = useLocale();
  return (
    <header className={styles.header}>
      <Link href={localeHref(locale, "/")} className={styles.brand} aria-label="Playalike home">
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
        <span className={styles.wordmark}>PLAYALIKE</span>
        <HeaderTagline />
      </Link>

      <div className={styles.actions}>
        <LanguageToggle />
      </div>
    </header>
  );
}
