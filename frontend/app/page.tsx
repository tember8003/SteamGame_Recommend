"use client";

import Header from "@/components/Header";
import Button from "@/components/ui/Button";
import { useT, useMessages } from "@/lib/i18n/useT";
import { siSteam } from "simple-icons";
import styles from "./page.module.css";

export default function Home() {
  const t = useT();
  const m = useMessages();
  return (
    <div className={styles.page}>
      <Header />

      <main className={styles.main}>
        <section className={styles.hero}>
          <h1 className={styles.title}>{t("landing.heroTitle")}</h1>
          <p className={styles.subtitle}>{t("landing.heroSubtitle")}</p>
        </section>

        <section className={styles.modes}>
          <article className={`${styles.card} ${styles.steamCard}`}>
            <div className={styles.cardBody}>
              <span className={`${styles.mark} ${styles.steamMark}`}>
                <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <path d={siSteam.path} />
                </svg>
              </span>
              <h2 className={styles.cardTitle}>
                STEAM <span className={styles.steamAccent}>{t("landing.steamPicks")}</span>
              </h2>
              <p className={styles.cardDesc}>{t("landing.steamDesc")}</p>
            </div>
            <Button variant="steam" href="/steam" className={styles.cardCta}>
              {t("landing.steamCta")}
            </Button>
          </article>

          <article className={`${styles.card} ${styles.mcCard}`} aria-disabled="true">
            <span className={styles.comingSoon}>{t("landing.comingSoon")}</span>
            <div className={styles.cardBody}>
              <span className={`${styles.mark} ${styles.mcMark}`} aria-hidden="true">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <rect x="2" y="8" width="20" height="14" fill="#7a4e26" />
                  <rect x="2" y="3" width="20" height="6" fill="#6aa84f" />
                  <rect x="2" y="9" width="4" height="2" fill="#6aa84f" />
                  <rect x="9" y="9" width="4" height="2" fill="#6aa84f" />
                  <rect x="16" y="9" width="4" height="2" fill="#6aa84f" />
                  <rect x="6" y="13" width="2" height="2" fill="#5f3c1e" />
                  <rect x="10" y="16" width="2" height="2" fill="#5f3c1e" />
                  <rect x="15" y="18" width="2" height="2" fill="#5f3c1e" />
                </svg>
              </span>
              <h2 className={styles.cardTitle}>
                MINECRAFT <span className={styles.mcAccent}>{t("landing.mcModpacks")}</span>
              </h2>
              <p className={styles.cardDesc}>{t("landing.mcDesc")}</p>
              <div className={styles.tags}>
                {m.landing.mcTags.map((tag) => (
                  <span key={tag} className={styles.tag}>
                    {tag}
                  </span>
                ))}
              </div>
            </div>
            <Button variant="minecraft" disabled className={styles.cardCta}>
              {t("landing.mcCta")}
            </Button>
          </article>
        </section>

        <footer className={styles.footer}>{t("landing.footer")}</footer>
      </main>
    </div>
  );
}
