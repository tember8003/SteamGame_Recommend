"use client";
import { useRef, useState } from 'react';
import Header from '@/components/Header';
import SteamIdGuide from '@/components/SteamIdGuide';
import WorkshopIntro from '@/components/WorkshopIntro';
import type { Locale } from '@/lib/i18n/locales';
import { libraryAnalyze, type LibraryAnalysis } from '@/lib/library/api';
import { libraryCopy } from '@/lib/library/copy';
import { libraryError } from '@/lib/library/presentation';
import { ApiError } from '@/lib/api';
import LibraryReport from './LibraryReport';
import { useCooldown } from './useCooldown';
import styles from './library.module.css';

export default function LibraryWorkbench({ locale }: { locale: Locale }) {
  const c = libraryCopy[locale];
  const [input, setInput] = useState('');
  const [submittedInput, setSubmittedInput] = useState('');
  const [result, setResult] = useState<LibraryAnalysis | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [invalid, setInvalid] = useState(false);
  const [avatarFailed, setAvatarFailed] = useState(false);
  const [reportKey, setReportKey] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const lock = useRef(false);
  const cooldown = useCooldown();
  async function analyze() {
    if (lock.current || cooldown.remaining) return;
    if (!input.trim() || input.trim().length > 200) { setInvalid(true); setError(c.invalid); inputRef.current?.focus(); return; }
    lock.current = true; setLoading(true); setError(''); setInvalid(false); setResult(null);
    const value = input.trim();
    try {
      const data = await libraryAnalyze(value, locale);
      setSubmittedInput(value); setResult(data); setAvatarFailed(false); setReportKey(key => key + 1);
    } catch (cause) { setError(libraryError(cause, locale)); setInvalid(cause instanceof ApiError && (cause.status === 400 || cause.code === 'STEAM_PROFILE_NOT_FOUND')); cooldown.fromError(cause); }
    finally { lock.current = false; setLoading(false); }
  }
  return <><Header /><main className={styles.page}>
    <WorkshopIntro compact place="archive" title={c.title} subtitle={c.subtitle} />
    <section className={styles.inputPanel} aria-labelledby="library-input-title">
      <h2 id="library-input-title">{c.inputTitle}</h2>
      <form noValidate onSubmit={event => { event.preventDefault(); void analyze(); }}>
        <label htmlFor="library-steam-id">{c.input}</label>
        <div className={styles.inputRow}>
          <input className={styles.input} ref={inputRef} id="library-steam-id" name="steamId" value={input} maxLength={200} autoComplete="off" spellCheck={false} aria-describedby={error ? 'library-input-hint library-input-error' : 'library-input-hint'} aria-invalid={invalid} placeholder={c.placeholder} onChange={event => { setInput(event.target.value); setError(''); setInvalid(false); }} />
          <button className={styles.button} disabled={loading || cooldown.remaining > 0}>{loading ? c.loading : c.analyze}</button>
        </div>
      </form>
      <p className={styles.hint} id="library-input-hint">{c.hint}</p><SteamIdGuide />
      <p className={styles.hint}>{c.publicHint}</p>
      {loading ? <div role="status" className={styles.status}><p>{c.loading}</p><p>{c.loadingHint}</p></div> : null}
      {error ? <p id="library-input-error" role="alert" className={styles.error}>{error}{cooldown.remaining ? ` ${cooldown.remaining} ${c.wait}` : ''}</p> : null}
    </section>
    {result ? <>
      <header className={styles.resultHeader}>
        {result.profile.avatarUrl && !avatarFailed ? (
          // Steam avatars are small remote images, excluded from all exports.
          // eslint-disable-next-line @next/next/no-img-element
          <img src={result.profile.avatarUrl} width={64} height={64} alt="" referrerPolicy="no-referrer" onError={() => setAvatarFailed(true)} />
        ) : <span className={styles.avatar} aria-hidden="true">▤</span>}
        <div><h2>{c.result}</h2><p>{result.profile.personaName || c.anonymous}</p></div>
      </header>
      <LibraryReport key={reportKey} data={result} owner={result} locale={locale} submittedInput={submittedInput} onRefresh={setResult} />
    </> : !loading ? <div className={styles.ready}><p>{c.preview}</p><p>{c.ready}</p></div> : null}
  </main></>;
}
