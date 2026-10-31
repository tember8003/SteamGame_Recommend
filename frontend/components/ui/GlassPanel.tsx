import type { ElementType, HTMLAttributes, ReactNode } from "react";
import styles from "./GlassPanel.module.css";

// 기존 호출부와 이름을 유지하면서 목재 테두리 패널을 공통으로 제공한다.
export default function GlassPanel({
  as: Tag = "div",
  children,
  className,
  ...rest
}: {
  as?: ElementType;
  children: ReactNode;
  className?: string;
} & HTMLAttributes<HTMLElement>) {
  const cls = [styles.panel, className].filter(Boolean).join(" ");
  return (
    <Tag className={cls} {...rest}>
      {children}
    </Tag>
  );
}
