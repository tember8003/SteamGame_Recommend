import type { Metadata } from "next";
import "../globals.css";
import { gmarketSans } from "../fonts";
import styles from "./internal.module.css";

// 토큰이 필요한 내부 화면이라 검색 결과에 나오지 않게 한다
export const metadata: Metadata = {
  title: "지표 | Playalike",
  robots: { index: false, follow: false },
};

// 내부 지표 화면 전용 루트 레이아웃, 화면 문구가 한국어라 lang 은 ko 로 둔다
// 언어 주소와 언어 토글이 없어서 LocaleProvider 를 두지 않는다
// 우주 배경 대신 차분한 단색 배경을 쓴다, 사용자 화면의 2D 리뉴얼 배경과 무관하다
export default function InternalLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko" className={gmarketSans.variable}>
      <body className={styles.body}>
        {children}
      </body>
    </html>
  );
}
