import type { Metadata } from "next";
import { notFound } from "next/navigation";
import "../globals.css";
import { gmarketSans } from "../fonts";
import VillageBackground from "@/components/VillageBackground";
import { LocaleProvider } from "@/lib/i18n/LocaleProvider";
import { isLocale, LOCALES } from "@/lib/i18n/locales";
import { metadataFor } from "@/lib/seo/metadata";
import { SITE_URL } from "@/lib/seo/routes";

const homeMetadata = metadataFor("home");

// 홈 메타데이터와 함께 metadataBase 를 둔다, 아래 화면의 상대 주소가 모두 운영 주소 기준 절대 주소가 된다
export async function generateMetadata(props: { params: Promise<{ locale: string }> }): Promise<Metadata> {
  return { metadataBase: new URL(SITE_URL), ...(await homeMetadata(props)) };
}

// en, ko 두 언어의 화면을 빌드 때 미리 만든다
export function generateStaticParams() {
  return LOCALES.map((locale) => ({ locale }));
}

export default async function LocaleLayout({
  children,
  params,
}: Readonly<{ children: React.ReactNode; params: Promise<{ locale: string }> }>) {
  const { locale } = await params;
  // proxy 를 거치지 않는 확장자 주소(/foo.php 같은)가 이 자리로 들어오면 404 로 보낸다
  if (!isLocale(locale)) notFound();
  return (
    <html lang={locale} className={gmarketSans.variable}>
      <body>
        <VillageBackground />
        <LocaleProvider locale={locale}>{children}</LocaleProvider>
      </body>
    </html>
  );
}
