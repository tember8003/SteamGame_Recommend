"use client";

import { useRef } from "react";
import styles from "./SegmentedControl.module.css";

export type SegmentOption = { value: string; label: string };

type Props = {
  options: SegmentOption[];
  value: string;
  onChange: (value: string) => void;
  ariaLabel: string;
};

// 세그먼트 필 라디오 그룹, 네이티브 select 대신 다크 테마에 맞는 선택 컨트롤로 쓴다
export default function SegmentedControl({ options, value, onChange, ariaLabel }: Props) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);

  // 좌우 화살표로 이웃 필로 이동하고 그 값을 고른다
  function handleKey(e: React.KeyboardEvent, index: number) {
    if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
    e.preventDefault();
    const delta = e.key === "ArrowRight" ? 1 : -1;
    const next = (index + delta + options.length) % options.length;
    onChange(options[next].value);
    refs.current[next]?.focus();
  }

  return (
    <div className={styles.group} role="radiogroup" aria-label={ariaLabel}>
      {options.map((opt, i) => {
        const active = opt.value === value;
        return (
          <button
            key={opt.value}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="button"
            role="radio"
            aria-checked={active}
            tabIndex={active ? 0 : -1}
            className={`${styles.pill} ${active ? styles.active : ""}`}
            onClick={() => onChange(opt.value)}
            onKeyDown={(e) => handleKey(e, i)}
          >
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}
