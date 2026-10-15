import type { Metadata } from "next";
import "../globals.css";
import { gmarketSans } from "../fonts";
import CosmicBackground from "@/components/CosmicBackground";

export const metadata: Metadata = {
  title: "지표 | Playalike",
};

// 내부 지표 화면 전용 루트 레이아웃, 화면 문구가 한국어라 lang 은 ko 로 둔다
// 언어 주소와 언어 토글이 없어서 LocaleProvider 를 두지 않는다
export default function InternalLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko" className={gmarketSans.variable}>
      <body>
        <CosmicBackground />
        {children}
      </body>
    </html>
  );
}
