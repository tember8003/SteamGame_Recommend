"use client";

import { useT } from "@/lib/i18n/useT";
import styles from "./SteamIdGuide.module.css";

export default function SteamIdGuide() {
  const t = useT();
  return (
    <details className={styles.guide}>
      <summary>{t("steamGuide.title")}</summary>
      <ol>
        <li>{t("steamGuide.step1")} <a href="https://store.steampowered.com/account/" target="_blank" rel="noopener noreferrer">{t("steamGuide.accountLink")}</a></li>
        <li>{t("steamGuide.step2")}</li>
        <li>{t("steamGuide.step3")}</li>
      </ol>
      <p>{t("steamGuide.privacy")}</p>
      <a href="https://steamcommunity.com/my/edit/settings" target="_blank" rel="noopener noreferrer">{t("steamGuide.privacyLink")}</a>
    </details>
  );
}
