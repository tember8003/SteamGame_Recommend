export const LOCALES = ["en", "ko"] as const;
export type Locale = (typeof LOCALES)[number];
export const DEFAULT_LOCALE: Locale = "en";

// 토글로 고른 언어를 기억하는 쿠키, proxy 가 언어 없는 주소를 옮길 때 가장 먼저 본다
export const LOCALE_COOKIE = "locale";

export function isLocale(value: unknown): value is Locale {
  return (LOCALES as readonly unknown[]).includes(value);
}
