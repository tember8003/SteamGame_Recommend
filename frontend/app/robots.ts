import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/seo/routes";

// 모든 수집 프로그램(검색엔진, AI 검색 수집 프로그램 포함)에 전체를 연다
// /metrics 도 막지 않는다, 막으면 수집 프로그램이 그 화면의 noindex 를 읽지 못한다
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [{ userAgent: "*", allow: "/" }],
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
