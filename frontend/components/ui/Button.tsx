import Link from "next/link";
import type { ReactNode } from "react";
import styles from "./Button.module.css";

type Variant = "steam" | "glass";

// 공통 버튼, href 있으면 내부 링크(next/link), disabled 면 button 으로 렌더함
export default function Button({
  variant = "glass",
  children,
  href,
  disabled,
  className,
  ariaLabel,
}: {
  variant?: Variant;
  children: ReactNode;
  href?: string;
  disabled?: boolean;
  className?: string;
  ariaLabel?: string;
}) {
  const cls = [styles.btn, styles[variant], className].filter(Boolean).join(" ");
  if (href && !disabled) {
    return (
      <Link href={href} className={cls} aria-label={ariaLabel}>
        {children}
      </Link>
    );
  }
  return (
    <button className={cls} disabled={disabled} aria-label={ariaLabel}>
      {children}
    </button>
  );
}
