"use client";

import Header from "@/components/Header";
import Button from "@/components/ui/Button";
import { useT } from "@/lib/i18n/useT";
import { siSteam } from "simple-icons";
import styles from "./page.module.css";

export default function Home() {
  const t = useT();
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
        </section>

        <footer className={styles.footer}>{t("landing.footer")}</footer>
      </main>
    </div>
  );
}
