import type { ElementType, HTMLAttributes, ReactNode } from "react";
import styles from "./GlassPanel.module.css";

// 반투명 글래스 표면 컨테이너, 카드,패널 등에서 공통 배경으로 재사용함
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
