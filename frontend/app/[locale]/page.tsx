"use client";

import Header from "@/components/Header";
import Button from "@/components/ui/Button";
import { useLocale } from "@/lib/i18n/LocaleProvider";
import { localeHref } from "@/lib/i18n/routing";
import { useT } from "@/lib/i18n/useT";
import { siSteam } from "simple-icons";
import styles from "./page.module.css";

export default function Home() {
  const t = useT();
  const locale = useLocale();
  return (
    <div className={styles.page}>
      <Header />

      <main className={styles.main}>
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
            <Button variant="steam" href={localeHref(locale, "/steam")} className={styles.cardCta}>
              {t("landing.steamCta")}
            </Button>
          </article>
        </section>

        <footer className={styles.footer}>{t("landing.footer")}</footer>
      </main>
    </div>
  );
}
