"use client";
import Link from 'next/link';
import { useEffect, useState } from 'react';
import Header from '@/components/Header';
import WorkshopIntro from '@/components/WorkshopIntro';
import type { Locale } from '@/lib/i18n/locales';
import { libraryShared, type LibrarySnapshot } from '@/lib/library/api';
import { libraryCopy } from '@/lib/library/copy';
import { libraryError } from '@/lib/library/presentation';
import { recordTasteEvent } from '@/lib/taste/api';
import LibraryReport from './LibraryReport';
import styles from './library.module.css';

export default function LibraryShared({ locale, shareId }: { locale: Locale; shareId: string }) {
  const c = libraryCopy[locale];
  const [data, setData] = useState<LibrarySnapshot | null>(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const url = new URL(window.location.href);
    if (url.searchParams.get('s') === '1') {
      recordTasteEvent('SHARE_INFLOW', 'LIBRARY'); url.searchParams.delete('s');
      window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`);
    }
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    void libraryShared(shareId, locale, controller.signal).then(value => { if (!controller.signal.aborted) setData(value); }).catch(cause => { if (!controller.signal.aborted) setError(libraryError(cause, locale)); });
    return () => controller.abort();
  }, [shareId, locale, retry]);
  return <><Header /><main className={styles.page}>
    <WorkshopIntro compact place="archive" title={c.shared} subtitle={c.shareInfo} />
    {data ? <LibraryReport data={data} locale={locale} existingShareId={shareId} /> : error ? <div className={styles.notice}>
      <p role="alert">{error}</p><div className={styles.actions}><button className={styles.secondary} onClick={() => { setError(''); setRetry(value => value + 1); }}>{c.retry}</button><Link href={`/${locale}/library`}>{c.self}</Link></div>
    </div> : <p role="status" className={styles.notice}>{c.loading}</p>}
  </main></>;
}
