"use client";

import Link from "next/link";
import Header from "@/components/Header";
import WorkshopIntro, { type WorkshopPlace } from "@/components/WorkshopIntro";
import GlassPanel from "@/components/ui/GlassPanel";
import { useLocale } from "@/lib/i18n/LocaleProvider";
import { localeHref } from "@/lib/i18n/routing";
import { useT } from "@/lib/i18n/useT";
import styles from "./page.module.css";
import { recordTasteEvent } from "@/lib/taste/api";

type Feature = { key: string; href: string; cta: "start" | "play" };
const PLACES: { place: Exclude<WorkshopPlace, "village">; name: string; description: string; cards: Feature[] }[] = [
  { place: "fortune", name: "landing.fortuneName", description: "workshop.fortuneDesc", cards: [
    { key: "choice", href: "/steam/choice", cta: "play" },
    { key: "reviewGuess", href: "/steam/review-guess", cta: "play" },
  ] },
  { place: "explorer", name: "landing.explorerName", description: "workshop.exploreDesc", cards: [
    { key: "similar", href: "/steam/similar", cta: "start" },
    { key: "input", href: "/steam/input", cta: "start" },
    { key: "match", href: "/steam/match", cta: "start" },
    { key: "random", href: "/steam/random", cta: "start" },
    { key: "graph", href: "/steam/graph", cta: "start" },
  ] },
  { place: "archive", name: "landing.archiveName", description: "workshop.archiveDesc", cards: [
    { key: "library", href: "/library", cta: "start" },
    { key: "profile", href: "/steam/profile", cta: "start" },
    { key: "recent", href: "/steam/recent", cta: "start" },
  ] },
];

export default function SteamHubPage() {
  const t = useT();
  const locale = useLocale();
  return (
    <div className={styles.page}>
      <Header />
      <main className={styles.main}>
        <WorkshopIntro place="village" title={t("hub.title")} subtitle={t("hub.subtitle")} />
        {PLACES.map((group, placeIndex) => (
          <section className={styles.section} key={group.place} aria-labelledby={`place-${group.place}`}>
            <div className={styles.placeHeading}>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={`/images/village/${group.place}.webp`} width="144" height="108" alt="" />
              <div>
                <p className={styles.placeIndex}>0{placeIndex + 1}</p>
                <h2 id={`place-${group.place}`}>{t(group.name)}</h2>
                <p className={styles.placeDescription}>{t(group.description)}</p>
              </div>
            </div>
            <div className={styles.grid}>
              {group.cards.map((card, index) => (
                <GlassPanel as="article" className={styles.card} key={card.href}>
                  <Link href={localeHref(locale, card.href)} className={styles.cardLink} onClick={card.href === "/library" ? () => recordTasteEvent('FEATURE_SELECT', 'LIBRARY') : undefined}>
                    <span className={styles.cardNumber} aria-hidden="true">0{index + 1}</span>
                    <h3 className={styles.cardTitle}>{t(`hub.cards.${card.key}.title`)}</h3>
                    <p className={styles.cardDesc}>{t(`hub.cards.${card.key}.desc`)}</p>
                    <span className={styles.cta}>{t(`hub.cta.${card.cta}`)} <span aria-hidden="true">›</span></span>
                  </Link>
                </GlassPanel>
              ))}
            </div>
          </section>
        ))}
      </main>
    </div>
  );
}
