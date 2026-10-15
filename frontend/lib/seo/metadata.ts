import type { Metadata } from "next";
import { DEFAULT_LOCALE, isLocale, type Locale } from "@/lib/i18n/locales";
import { getMessages } from "@/lib/i18n/messages";
import { INDEX_ROUTES, SITE_NAME, type RouteKey } from "./routes";

const OG_LOCALE: Record<Locale, string> = { en: "en_US", ko: "ko_KR" };

// 화면 하나의 제목, 설명, canonical, hreflang, Open Graph 를 만든다
// 주소는 상대 경로로 두고 루트 레이아웃의 metadataBase 가 절대 주소로 바꾼다
export function buildMetadata(locale: Locale, route: RouteKey): Metadata {
  const path = INDEX_ROUTES[route];
  const text = getMessages(locale).meta[route];
  const title = route === "home" ? text.title : `${text.title} | ${SITE_NAME}`;
  const url = `/${locale}${path}`;
  return {
    title,
    description: text.description,
    alternates: {
      canonical: url,
      // x-default 는 언어를 골라 옮겨 주는 언어 없는 주소다
      languages: { ko: `/ko${path}`, en: `/en${path}`, "x-default": path === "" ? "/" : path },
    },
    openGraph: {
      title,
      description: text.description,
      url,
      siteName: SITE_NAME,
      locale: OG_LOCALE[locale],
      type: "website",
      // 화면 레이아웃의 openGraph 가 파일 기반 이미지를 덮으므로 이미지 주소를 직접 적는다
      images: [{ url: `/${locale}/opengraph-image`, width: 1200, height: 630, alt: SITE_NAME }],
    },
  };
}

// 화면 폴더의 layout.tsx 가 그대로 내보낼 generateMetadata 를 만든다
export function metadataFor(route: RouteKey) {
  return async function generateMetadata({
    params,
  }: {
    params: Promise<{ locale: string }>;
  }): Promise<Metadata> {
    const { locale } = await params;
    return buildMetadata(isLocale(locale) ? locale : DEFAULT_LOCALE, route);
  };
}
