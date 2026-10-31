"use client";

import { useT } from "@/lib/i18n/useT";
import styles from "./WorkshopIntro.module.css";

export type WorkshopPlace = "fortune" | "explorer" | "archive" | "village";

const PLACE_NAMES = {
  fortune: "landing.fortuneName",
  explorer: "landing.explorerName",
  archive: "landing.archiveName",
  village: "workshop.village",
};

export default function WorkshopIntro({ title, subtitle, place }: {
  title: string;
  subtitle: string;
  place: WorkshopPlace;
}) {
  const t = useT();
  return (
    <section className={styles.intro}>
      <div className={styles.copy}>
        <p className={styles.location}><span aria-hidden="true">◆</span> {t(PLACE_NAMES[place])}</p>
        <h1>{title}</h1>
        <p className={styles.subtitle}>{subtitle}</p>
      </div>
      {place !== "village" ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img className={styles.shop} src={`/images/village/${place}.webp`} width="240" height="180" alt="" />
      ) : <span className={styles.seal} aria-hidden="true">✦</span>}
    </section>
  );
}
