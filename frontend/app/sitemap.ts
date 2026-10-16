import type { MetadataRoute } from "next";
import { LOCALES } from "@/lib/i18n/locales";
import { INDEX_ROUTES, SITE_URL } from "@/lib/seo/routes";

// 색인 대상 화면을 언어별로 하나씩 싣고, 같은 화면의 다른 언어 주소를 alternates 로 묶는다
// 실제 수정 시각을 알 수 없어서 lastModified 는 넣지 않는다, 빌드 시각을 넣으면 거짓 신호가 된다
export default function sitemap(): MetadataRoute.Sitemap {
  return Object.values(INDEX_ROUTES).flatMap((path) => {
    const languages = { ko: `${SITE_URL}/ko${path}`, en: `${SITE_URL}/en${path}` };
    return LOCALES.map((locale) => ({
      url: `${SITE_URL}/${locale}${path}`,
      alternates: { languages },
    }));
  });
}
