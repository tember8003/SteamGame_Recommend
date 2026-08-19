"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import Header from "@/components/Header";
import GlassPanel from "@/components/ui/GlassPanel";
import { useT } from "@/lib/i18n/useT";
import styles from "./page.module.css";

// 카드 한 장의 데이터 형태, 문구는 key 로 카탈로그를 가리키고 icon 은 인라인 svg 를 그대로 담음
type FeatureCard = {
  key: string;
  href: string;
  cta: "start" | "play";
  icon: ReactNode;
};

// 이동 화살표, cta 텍스트 옆에 붙임
function ArrowIcon() {
  return (
    <svg
      className={styles.ctaIcon}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  );
}

const RECOMMENDATION_CARDS: FeatureCard[] = [
  {
    key: "random",
    href: "/steam/random",
    cta: "start",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
        <rect x="4" y="4" width="16" height="16" rx="3.5" />
        <circle cx="8.5" cy="8.5" r="1.15" fill="currentColor" stroke="none" />
        <circle cx="15.5" cy="8.5" r="1.15" fill="currentColor" stroke="none" />
        <circle cx="12" cy="12" r="1.15" fill="currentColor" stroke="none" />
        <circle cx="8.5" cy="15.5" r="1.15" fill="currentColor" stroke="none" />
        <circle cx="15.5" cy="15.5" r="1.15" fill="currentColor" stroke="none" />
      </svg>
    ),
  },
  {
    key: "input",
    href: "/steam/input",
    cta: "start",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
        <rect x="3" y="6" width="18" height="12" rx="2" />
        <rect x="6" y="9" width="2" height="2" fill="currentColor" stroke="none" />
        <rect x="11" y="9" width="2" height="2" fill="currentColor" stroke="none" />
        <rect x="16" y="9" width="2" height="2" fill="currentColor" stroke="none" />
        <rect x="6" y="13" width="12" height="2" rx="1" fill="currentColor" stroke="none" />
      </svg>
    ),
  },
  {
    key: "match",
    href: "/steam/match",
    cta: "start",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
        <path d="M4 7h10M4 12h16M4 17h7" strokeLinecap="round" />
        <circle cx="18" cy="7" r="1.4" fill="currentColor" stroke="none" />
      </svg>
    ),
  },
  {
    key: "profile",
    href: "/steam/profile",
    cta: "start",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
        <circle cx="12" cy="8.5" r="3.2" />
        <path d="M5 19c1.4-3.6 4.4-5.4 7-5.4s5.6 1.8 7 5.4" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    key: "recent",
    href: "/steam/recent",
    cta: "start",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
        <circle cx="12" cy="12" r="8.2" />
        <path d="M12 7.5v5l3.4 2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    ),
  },
  {
    key: "similar",
    href: "/steam/similar",
    cta: "start",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
        <circle cx="9.5" cy="12" r="6.2" />
        <circle cx="14.5" cy="12" r="6.2" opacity="0.55" />
      </svg>
    ),
  },
  {
    key: "graph",
    href: "/steam/graph",
    cta: "start",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
        <path d="M6 17l5-10 6 6" strokeLinecap="round" strokeLinejoin="round" />
        <circle cx="6" cy="17" r="1.6" fill="currentColor" stroke="none" />
        <circle cx="11" cy="7" r="1.6" fill="currentColor" stroke="none" />
        <circle cx="17" cy="13" r="1.6" fill="currentColor" stroke="none" />
      </svg>
    ),
  },
];

const GAME_CARDS: FeatureCard[] = [
  {
    key: "choice",
    href: "/steam/choice",
    cta: "play",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" aria-hidden="true">
        <path d="M12 19.5s-7-4.3-9.3-8.7C1.3 7.9 2.8 4.8 6 4.8c2 0 3.4 1.1 4.2 2.5.8-1.4 2.2-2.5 4.2-2.5 3.2 0 4.7 3.1 3.3 6-2.3 4.4-9.3 8.7-9.3 8.7z" />
      </svg>
    ),
  },
  {
    key: "reviewGuess",
    href: "/steam/review-guess",
    cta: "play",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
        <path d="M9 9a3 3 0 1 1 4.2 2.7c-1 .5-1.7 1.1-1.7 2.3" strokeLinecap="round" strokeLinejoin="round" />
        <circle cx="11.5" cy="17.3" r="0.9" fill="currentColor" stroke="none" />
      </svg>
    ),
  },
];

// 카드 한 장, 전체 표면은 GlassPanel 이 맡고 링크 하나가 표면을 꽉 채워 카드 전체를 클릭 영역으로 만듦
// 안쪽에 실제 버튼,링크를 또 넣지 않아 중첩 인터랙티브 요소를 피함
function FeatureCard({ card, accent }: { card: FeatureCard; accent: "rec" | "game" }) {
  const t = useT();
  const accentCls = accent === "rec" ? styles.recAccent : styles.gameAccent;
  return (
    <GlassPanel as="article" className={`${styles.card} ${accentCls}`}>
      <Link href={card.href} className={styles.cardLink}>
        <span className={styles.icon}>{card.icon}</span>
        <h3 className={styles.cardTitle}>{t(`hub.cards.${card.key}.title`)}</h3>
        <p className={styles.cardDesc}>{t(`hub.cards.${card.key}.desc`)}</p>
        <span className={styles.cta}>
          {t(`hub.cta.${card.cta}`)}
          <ArrowIcon />
        </span>
      </Link>
    </GlassPanel>
  );
}

// steam 기능 허브, 원본 index.html 의 8개 카드를 문구 그대로 옮기고 라우트만 /steam/* 로 매핑함
// 우주 배경(CosmicBackground)은 루트 레이아웃이 이미 그려서 다시 넣지 않음
// 헤더는 layout.tsx 가 아니라 각 페이지가 직접 그리는 기존 구조라 랜딩(app/page.tsx)과 같은 방식으로 그림
export default function SteamHubPage() {
  const t = useT();
  return (
    <div className={styles.page}>
      <Header />

      <main className={styles.main}>
        <section className={styles.intro}>
          <h1 className={styles.title}>{t("hub.title")}</h1>
          <p className={styles.subtitle}>{t("hub.subtitle")}</p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>{t("hub.recSection")}</h2>
          <div className={styles.grid}>
            {RECOMMENDATION_CARDS.map((card) => (
              <FeatureCard key={card.href} card={card} accent="rec" />
            ))}
          </div>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>{t("hub.gameSection")}</h2>
          <div className={`${styles.grid} ${styles.gridTwo}`}>
            {GAME_CARDS.map((card) => (
              <FeatureCard key={card.href} card={card} accent="game" />
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}
