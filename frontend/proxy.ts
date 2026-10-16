import { NextResponse, type NextRequest } from "next/server";
import { LOCALE_COOKIE } from "@/lib/i18n/locales";
import { localeRedirectPath, pickLocale } from "@/lib/i18n/routing";

// 언어가 없는 주소를 언어 주소로 옮긴다, 사람마다 이동 대상이 달라 영구 이동(308)이 아닌 307 을 쓴다
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const locale = pickLocale(request.cookies.get(LOCALE_COOKIE)?.value, request.headers.get("accept-language"));
  const target = localeRedirectPath(pathname, search, locale);
  if (target === null) return NextResponse.next();
  return NextResponse.redirect(new URL(target, request.url), 307);
}

export const config = {
  // Next 내부 파일과 확장자가 있는 파일(robots.txt, sitemap.xml, favicon.ico, Search Console 확인 html)은 건너뛴다
  matcher: ["/((?!_next/|.*\\..*).*)"],
};
