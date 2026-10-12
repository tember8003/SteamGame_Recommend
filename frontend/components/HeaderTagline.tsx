"use client";

import styles from "./Header.module.css";
import { useT } from "@/lib/i18n/useT";

// 브랜드 옆 부제, 언어 토글을 따라가야 해서 클라이언트 컴포넌트로 분리함
export default function HeaderTagline() {
  const t = useT();
  return <span className={styles.tagline}>{t("header.tagline")}</span>;
}
