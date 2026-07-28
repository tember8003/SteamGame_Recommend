import type { Metadata } from "next";
import { Geist } from "next/font/google";
import "./globals.css";
import CosmicBackground from "@/components/CosmicBackground";

const geist = Geist({ variable: "--font-geist-sans", subsets: ["latin"] });

export const metadata: Metadata = {
  title: "PLAYLOOM — Find your next way to play",
  description:
    "Discover great Steam games or build the perfect Minecraft modpack, guided by your AI concierge.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={geist.variable}>
      <body>
        <CosmicBackground />
        {children}
      </body>
    </html>
  );
}
