import { DEFAULT_LOCALE, isLocale, type Locale } from "./locales";

// Accept-Language 를 품질값 순서로 보다가 처음 나오는 지원 언어를 고른다, 품질값 0 은 받지 않는다는 뜻이라 뺀다
function fromAcceptLanguage(header: string | null | undefined): Locale | undefined {
  if (!header) return undefined;
  const ranked = header
    .split(",")
    .map((part, index) => {
      const [tag, ...params] = part.trim().split(";");
      const qParam = params.map((p) => p.trim()).find((p) => p.startsWith("q="));
      const q = qParam ? Number(qParam.slice(2)) : 1;
      return { primary: tag.trim().toLowerCase().split("-")[0], q: Number.isFinite(q) ? q : 0, index };
    })
    .filter((item) => item.q > 0)
    .sort((a, b) => b.q - a.q || a.index - b.index);
  return ranked.map((item) => item.primary).find(isLocale);
}

// 쿠키, 브라우저 언어, 기본값 순서로 언어를 고른다
export function pickLocale(cookie: string | undefined, acceptLanguage: string | null | undefined): Locale {
  if (isLocale(cookie)) return cookie;
  return fromAcceptLanguage(acceptLanguage) ?? DEFAULT_LOCALE;
}

// 첫 경로 조각이 언어면 그 언어를 돌려준다
export function localeOfPath(pathname: string): Locale | undefined {
  const first = pathname.split("/")[1];
  return isLocale(first) ? first : undefined;
}

// proxy 가 옮길 주소를 정한다, 옮기지 않을 경로면 null
// /metrics 는 언어 주소가 없는 내부 화면이라 그대로 둔다
export function localeRedirectPath(pathname: string, search: string, locale: Locale): string | null {
  if (pathname === "/metrics" || pathname.startsWith("/metrics/")) return null;
  if (localeOfPath(pathname)) return null;
  return `/${locale}${pathname === "/" ? "" : pathname}${search}`;
}

// 언어가 붙은 내부 주소를 만든다, path 는 "/" 또는 "/steam/..." 형태
export function localeHref(locale: Locale, path: string): string {
  return path === "/" ? `/${locale}` : `/${locale}${path}`;
}

// 현재 주소의 언어만 바꾼 주소를 만든다, 언어가 없는 주소면 앞에 붙인다
export function switchLocalePath(pathname: string, target: Locale): string {
  const current = localeOfPath(pathname);
  const rest = current ? pathname.slice(current.length + 1) : pathname;
  return localeHref(target, rest === "" || rest === "/" ? "/" : rest);
}
