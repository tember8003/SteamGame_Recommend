"use client";

import { createContext, useContext } from "react";
import type { Locale } from "./locales";

export type { Locale } from "./locales";

// 기본값은 en, 프로바이더 밖(테스트 등)에서도 throw 하지 않고 영어로 동작한다
const LocaleContext = createContext<Locale>("en");

// 언어는 주소의 [locale] 에서만 정해진다, 레이아웃이 넘긴 값을 그대로 내려보낸다
export function LocaleProvider({ locale, children }: { locale: Locale; children: React.ReactNode }) {
  return <LocaleContext.Provider value={locale}>{children}</LocaleContext.Provider>;
}

export function useLocale(): Locale {
  return useContext(LocaleContext);
}
