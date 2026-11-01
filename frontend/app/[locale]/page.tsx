"use client";

import Header from "@/components/Header";
import VillageNpc from "@/components/VillageNpc";
import Link from "next/link";
import { useLocale } from "@/lib/i18n/LocaleProvider";
import { localeHref } from "@/lib/i18n/routing";
import { useT } from "@/lib/i18n/useT";
import styles from "./page.module.css";
import { useEffect } from 'react';
import { recordLandingVisit, recordTasteEvent } from '@/lib/taste/api';

export default function Home() {
  const t = useT();
  const locale = useLocale();
  useEffect(() => { recordLandingVisit() }, []);
  return (
    <div className={styles.page}>
      <a className={styles.skipLink} href="#village">{t("landing.skip")}</a>
      <div className={styles.headerShell}><Header /></div>
      <div className={styles.fireflies} aria-hidden="true">
        {Array.from({ length: 8 }, (_, index) => <i key={index} />)}
      </div>
      <main id="village" className={styles.main} tabIndex={-1}>
        <h1 className={styles.srOnly}>{t("landing.places")}</h1>

        <section className={styles.village} aria-label={t("landing.places")}>
          <article className={styles.place}>
            <Link className={styles.placeLink} href={localeHref(locale, "/taste/test")} onClick={() => recordTasteEvent('FEATURE_SELECT', 'TASTE')}>
              <div className={styles.sign}>
                <span className={styles.placeNumber}>01 / {t("landing.fortuneName")}</span>
                <h2>{t("landing.tasteTitle")}</h2>
                <p>{t("landing.tasteDesc")}</p>
              </div>
              <div className={styles.art}>
                <span className={styles.bubble}>{t("landing.tasteGreeting")}</span>
                {/* 글자와 조작 영역을 이미지 밖에 두어 번역과 키보드 접근을 유지한다. */}
                <VillageNpc npc="fortune" />
              </div>
              <span className={styles.cta}>{t("landing.tasteCta")} <span aria-hidden="true">›</span></span>
            </Link>
          </article>

          <article className={`${styles.place} ${styles.explorer}`}>
            <details className={styles.searchOptions}>
              <summary className={styles.placeLink} onClick={event => { if (!event.currentTarget.parentElement?.hasAttribute('open')) recordTasteEvent('FEATURE_SELECT', 'SIMILAR') }}>
                <div className={styles.sign}>
                  <span className={styles.placeNumber}>02 / {t("landing.explorerName")}</span>
                  <h2>{t("landing.similarTitle")}</h2>
                  <p>{t("landing.similarDesc")}</p>
                </div>
                <div className={styles.art}>
                  <span className={styles.bubble}>{t("landing.similarGreeting")}</span>
                  <VillageNpc npc="explorer" />
                </div>
                <span className={styles.cta}>{t("landing.similarCta")} <span className={styles.chevron} aria-hidden="true">›</span></span>
              </summary>
              <nav className={styles.searchMenu} aria-label={t("landing.searchMethods")}>
                <p>{t("landing.searchPrompt")}</p>
                <Link href={localeHref(locale, "/steam/similar")}>{t("landing.byGame")}<span aria-hidden="true">↗</span></Link>
                <Link href={localeHref(locale, "/steam/input")}>{t("landing.byAi")}<span aria-hidden="true">↗</span></Link>
                <Link href={localeHref(locale, "/steam/random")}>{t("landing.byTags")}<span aria-hidden="true">↗</span></Link>
              </nav>
            </details>
          </article>

          <article className={styles.place}>
            <Link className={styles.placeLink} href={localeHref(locale, "/library")} onClick={() => recordTasteEvent('FEATURE_SELECT', 'LIBRARY')}>
              <div className={styles.sign}>
                <span className={styles.placeNumber}>03 / {t("landing.archiveName")}</span>
                <h2>{t("landing.libraryTitle")}</h2>
                <p>{t("landing.libraryDesc")}</p>
              </div>
              <div className={styles.art}>
                <span className={styles.bubble}>{t("landing.libraryGreeting")}</span>
                <VillageNpc npc="archive" />
              </div>
              <span className={styles.cta}>{t("landing.libraryCta")} <span aria-hidden="true">›</span></span>
            </Link>
          </article>
        </section>

        <p className={styles.trailNote}><span aria-hidden="true">✦</span> {t("landing.trailNote")}</p>
        <footer className={styles.footer}>
          <nav aria-label={t("landing.more")}>
            <Link href={localeHref(locale, "/taste")}>{locale === "ko" ? "16가지 게이머 도감" : "16 player characters"}</Link>
            <Link href={localeHref(locale, "/steam/choice")}>{locale === "ko" ? "게임 선택 월드컵" : "Game-pick tournament"}</Link>
            <Link href={localeHref(locale, "/steam")}>{t("landing.allFeatures")}</Link>
            <Link href={localeHref(locale, "/steam/review-guess")}>{t("landing.reviewGuess")}</Link>
            <Link href={localeHref(locale, "/steam/random")}>{t("landing.random")}</Link>
          </nav>
          <p>{t("landing.footer")}</p>
        </footer>
      </main>
    </div>
  );
}
