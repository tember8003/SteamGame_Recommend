import type { Metadata } from "next";
import localFont from "next/font/local";
import "./globals.css";
import CosmicBackground from "@/components/CosmicBackground";
import { LocaleProvider } from "@/lib/i18n/LocaleProvider";

// 지마켓 산스 셀프 호스팅, 라틴과 한글을 함께 이 폰트로 씀
// UI 는 normal 과 600,700,800 만 써서 Medium(본문), Bold(굵게) 둘이면 전부 커버됨, Light 는 안 씀
const gmarketSans = localFont({
  src: [
    { path: "./fonts/GmarketSansTTFMedium.ttf", weight: "500", style: "normal" },
    { path: "./fonts/GmarketSansTTFBold.ttf", weight: "700", style: "normal" },
  ],
  variable: "--font-gmarket-sans",
  display: "swap",
});

export const metadata: Metadata = {
  title: "PLAYLOOM — Find your next way to play",
  description:
    "Discover great Steam games or build the perfect Minecraft modpack, guided by your AI concierge.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={gmarketSans.variable}>
      <body>
        <CosmicBackground />
        <LocaleProvider>{children}</LocaleProvider>
      </body>
    </html>
  );
}
