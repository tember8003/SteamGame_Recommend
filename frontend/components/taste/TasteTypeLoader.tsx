"use client";
import { useEffect, useState } from 'react';
import Link from 'next/link';
import type { Locale } from '@/lib/i18n/locales';
import { catalogFromApi, recordTasteEvent, tasteType, tasteErrorText, type TypeView } from '@/lib/taste/api';
import { useTasteSession } from '@/lib/taste/session';
import TasteDetail from './TasteDetail';
import styles from './taste.module.css';

export default function TasteTypeLoader({ code, locale }: { code: string; locale: Locale }) {
  const session = useTasteSession();
  const [sharedEntry] = useState(() => typeof window !== 'undefined' && new URL(window.location.href).searchParams.get('s') === '1');
  const result = !sharedEntry && session.locale === locale && session.result?.code === code ? session.result : null;
  const immediate = result && session.locale === locale ? result.type : null;
  const [view, setView] = useState<TypeView | null>(immediate);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const url = new URL(window.location.href);
    if (url.searchParams.get('s') === '1') {
      recordTasteEvent('SHARE_INFLOW', code);
      url.searchParams.delete('s');
      window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`);
    }
  }, [code]);
  useEffect(() => {
    if (immediate) return;
    const controller = new AbortController();
    tasteType(code, locale, controller.signal).then(value => { if (!controller.signal.aborted) setView(value) })
      .catch(error => { if (!controller.signal.aborted) setError(tasteErrorText(error, locale)) });
    return () => controller.abort();
  }, [code, locale, immediate, attempt]);
  if (view) return <TasteDetail type={catalogFromApi(view, locale)} apiType={view} result={result ?? undefined} locale={locale} />;
  return <main id="taste-main" className={styles.main}><div className={styles.empty} aria-busy={!error}>
    {error ? <><p role="alert">{error}</p><button className={styles.primaryButton} onClick={() => { setError(''); setAttempt(n => n + 1) }}>{locale === 'ko' ? '다시 시도' : 'Try again'}</button></> :
      <p role="status">{locale === 'ko' ? '유형을 불러오고 있어요…' : 'Loading this play style…'}</p>}
    <Link href={`/${locale}/taste`}>{locale === 'ko' ? '전체 도감' : 'All types'}</Link>
  </div></main>;
}
