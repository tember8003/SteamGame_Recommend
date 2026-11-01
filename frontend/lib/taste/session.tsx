"use client";
import { createContext, useContext, useMemo, useState } from 'react';
import type { Locale } from '../i18n/locales';
import type { TasteResult } from './api';

type Session = { result: TasteResult | null; locale: Locale; save: (result: TasteResult, locale: Locale) => void; clear: () => void };
const Context = createContext<Session>({ result: null, locale: 'ko', save: () => {}, clear: () => {} });
export function TasteSessionProvider({ children }: { children: React.ReactNode }) {
  const [current, setCurrent] = useState<{ result: TasteResult | null; locale: Locale }>({ result: null, locale: 'ko' });
  const value = useMemo(() => ({ ...current, save: (result: TasteResult, locale: Locale) => setCurrent({ result, locale }), clear: () => setCurrent(value => ({ ...value, result: null })) }), [current]);
  return <Context.Provider value={value}>{children}</Context.Provider>;
}
export const useTasteSession = () => useContext(Context);
