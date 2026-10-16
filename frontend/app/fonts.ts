import localFont from "next/font/local";

// 지마켓 산스 셀프 호스팅, 라틴과 한글을 함께 이 폰트로 씀
// UI 는 normal 과 600,700,800 만 써서 Medium(본문), Bold(굵게) 둘이면 전부 커버됨, Light 는 안 씀
// 루트 레이아웃이 [locale] 과 (internal) 둘이라 여기 한 곳에 둔다
export const gmarketSans = localFont({
  src: [
    { path: "./fonts/GmarketSansTTFMedium.ttf", weight: "500", style: "normal" },
    { path: "./fonts/GmarketSansTTFBold.ttf", weight: "700", style: "normal" },
  ],
  variable: "--font-gmarket-sans",
  display: "swap",
});
