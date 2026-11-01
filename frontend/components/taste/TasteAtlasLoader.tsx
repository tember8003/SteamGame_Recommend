"use client";
import { useEffect, useState } from 'react';
import type { Locale } from '@/lib/i18n/locales';
import { catalogFromApi, tasteSummaries, tasteErrorText } from '@/lib/taste/api';
import type { TasteType } from '@/lib/taste/catalog';
import TasteAtlas from './TasteAtlas';
import styles from './taste.module.css';

export default function TasteAtlasLoader({ locale }: { locale: Locale }) {
  const [types, setTypes] = useState<TasteType[] | null>(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    tasteSummaries(locale, controller.signal).then(items => {
      if (!controller.signal.aborted) setTypes(items.map(item => catalogFromApi(item, locale)));
    }).catch(error => { if (!controller.signal.aborted) setError(tasteErrorText(error, locale)) });
    return () => controller.abort();
  }, [locale, attempt]);
  if (types) return <TasteAtlas locale={locale} types={types} />;
  return <main id="taste-main" className={styles.main}><div className={styles.empty} aria-busy={!error}>
    {error ? <><p role="alert">{error}</p><button className={styles.primaryButton} onClick={() => { setError(''); setAttempt(n => n + 1) }}>{locale === 'ko' ? '다시 시도' : 'Try again'}</button></> :
      <p role="status">{locale === 'ko' ? '게이머 도감을 불러오고 있어요…' : 'Opening the player atlas…'}</p>}
  </div></main>;
}
