"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import styles from "./ConciergeBar.module.css";

type Quick = { label: string; href: string; disabled?: boolean };

// v0 은 AI 없이 입력을 Steam 텍스트 추천으로 프리필해 넘김, v2 에서 실제 콘시어지 챗으로 교체함
const QUICK: Quick[] = [
  { label: "Recommend Steam games", href: "/steam/input" },
  { label: "Build a modpack", href: "#", disabled: true },
  { label: "Singleplayer", href: "/steam/input?q=singleplayer" },
  { label: "Low-spec", href: "/steam/input?q=low-spec" },
];

export default function ConciergeBar() {
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
        <span className={styles.spark}>✦</span> Ask concierge…
      </div>
      <form className={styles.form} onSubmit={submit}>
        <input
          className={styles.input}
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Ask anything about games, modpacks, or how to play…"
          aria-label="Ask the concierge"
        />
        <button className={styles.send} type="submit" aria-label="Send">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
            <path d="M4 12l16-7-7 16-2-7-7-2z" />
          </svg>
        </button>
      </form>
      <div className={styles.quick}>
        {QUICK.map((qb) =>
          qb.disabled ? (
            <span key={qb.label} className={styles.chip} data-disabled="true">
              {qb.label}
            </span>
          ) : (
            <a key={qb.label} className={styles.chip} href={qb.href}>
              {qb.label}
            </a>
          )
        )}
      </div>
    </div>
  );
}
