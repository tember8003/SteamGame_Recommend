"use client";

import { createContext, useContext, useEffect, useState } from "react";

export type Locale = "en" | "ko";

const STORAGE_KEY = "sgr_locale";

type LocaleContextValue = {
  locale: Locale;
  setLocale: (next: Locale) => void;
};

// 기본값은 en, 프로바이더 밖에서도 throw 하지 않고 영어로 동작한다
const LocaleContext = createContext<LocaleContextValue>({
  locale: "en",
  setLocale: () => {},
});

// 저장값이나 브라우저 언어에서 로케일을 고른다, 저장값이 최우선이고 못 고르면 en
function resolveInitialLocale(): Locale {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    if (saved === "ko" || saved === "en") return saved;
  } catch {
    // localStorage 접근이 막힌 환경이면 브라우저 언어로 넘어간다
  }
  return window.navigator.language.startsWith("ko") ? "ko" : "en";
}

export function LocaleProvider({ children }: { children: React.ReactNode }) {
  // 서버,첫 클라 렌더를 en 으로 맞춰 하이드레이션 불일치를 없앤다, 실제 선호는 마운트 뒤 보정한다
  const [locale, setLocaleState] = useState<Locale>("en");

  useEffect(() => {
    const resolved = resolveInitialLocale();
    if (resolved !== "en") {
      // 서버,첫 렌더는 en 으로 고정하고 마운트 뒤 한 번만 실제 선호로 보정한다, 이 setState 는 하이드레이션 안전을 위한 의도된 것임
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setLocaleState(resolved);
      document.documentElement.lang = resolved;
    }
  }, []);

  function setLocale(next: Locale) {
    setLocaleState(next);
    try {
      window.localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // 저장 실패는 무시한다, 세션 안에서는 상태로 계속 동작한다
    }
    document.documentElement.lang = next;
  }

  return (
    <LocaleContext.Provider value={{ locale, setLocale }}>
      {children}
    </LocaleContext.Provider>
  );
}

export function useLocale(): LocaleContextValue {
  return useContext(LocaleContext);
}
