"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useT } from "@/lib/i18n/useT";
import styles from "./ConciergeBar.module.css";

type Quick = { key: string; href: string; disabled?: boolean };

// v0 은 AI 없이 입력을 Steam 텍스트 추천으로 프리필해 넘김, v2 에서 실제 콘시어지 챗으로 교체함
const QUICK: Quick[] = [
  { key: "recommendSteam", href: "/steam/input" },
  { key: "buildModpack", href: "#", disabled: true },
  { key: "singleplayer", href: "/steam/input?q=singleplayer" },
  { key: "lowSpec", href: "/steam/input?q=low-spec" },
];

export default function ConciergeBar() {
  const t = useT();
  const [q, setQ] = useState("");
  const router = useRouter();

  function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const text = q.trim();
    router.push(text ? `/steam/input?q=${encodeURIComponent(text)}` : "/steam/input");
  }

  return (
    <div className={styles.panel}>
      <div className={styles.head}>
        <span className={styles.spark}>✦</span> {t("concierge.head")}
      </div>
      <form className={styles.form} onSubmit={submit}>
        <input
          className={styles.input}
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder={t("concierge.placeholder")}
          aria-label={t("concierge.inputLabel")}
        />
        <button className={styles.send} type="submit" aria-label={t("concierge.send")}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
            <path d="M4 12l16-7-7 16-2-7-7-2z" />
          </svg>
        </button>
      </form>
      <div className={styles.quick}>
        {QUICK.map((qb) =>
          qb.disabled ? (
            <span key={qb.key} className={styles.chip} data-disabled="true">
              {t(`concierge.quick.${qb.key}`)}
            </span>
          ) : (
            <a key={qb.key} className={styles.chip} href={qb.href}>
              {t(`concierge.quick.${qb.key}`)}
            </a>
          )
        )}
      </div>
    </div>
  );
}
