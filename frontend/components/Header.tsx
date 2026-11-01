"use client";

import Link from "next/link";
import styles from "./Header.module.css";
import HeaderTagline from "./HeaderTagline";
import LanguageToggle from "./LanguageToggle";
import { useLocale } from "@/lib/i18n/LocaleProvider";
import { localeHref } from "@/lib/i18n/routing";
import { usePathname } from "next/navigation";
import { useT } from "@/lib/i18n/useT";
import { recordTasteEvent } from "@/lib/taste/api";

// 상단 브랜드 바, 왼쪽은 로고와 부제, 오른쪽은 언어 토글
export default function Header() {
  const locale = useLocale();
  const pathname = usePathname();
  const t = useT();
  const inWorkshop = /\/(steam|library)(?:\/|$)/.test(pathname ?? "");
  const activePlace = /\/steam\/(similar|input|match|random|graph)(?:\/|$)/.test(pathname ?? "")
    ? "/steam/similar"
    : /\/library(?:\/|$)|\/steam\/(profile|recent)(?:\/|$)/.test(pathname ?? "")
      ? "/library"
      : /\/steam\/(choice|review-guess)(?:\/|$)/.test(pathname ?? "")
        ? "/steam/choice"
        : undefined;
  const places = [
    { href: "/", label: "workshop.back" },
    { href: "/steam", label: "landing.allFeatures" },
    { href: "/steam/choice", label: "landing.fortuneName" },
    { href: "/steam/similar", label: "landing.explorerName" },
    { href: "/library", label: "landing.archiveName" },
  ];
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
      {inWorkshop ? (
        <nav className={styles.navigation} aria-label={t("workshop.navigation")}>
          {places.map(({ href, label }) => (
            <Link key={href} href={localeHref(locale, href)} onClick={href === "/library" ? () => recordTasteEvent('FEATURE_SELECT', 'LIBRARY') : undefined} aria-current={pathname === localeHref(locale, href) ? "page" : href === activePlace ? "location" : undefined}>
              {href === "/" ? <span aria-hidden="true">← </span> : null}{t(label)}
            </Link>
          ))}
        </nav>
      ) : null}
    </header>
  );
}
