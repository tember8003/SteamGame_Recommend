"use client";
import Link from 'next/link';
import { useRef, useState } from 'react';
import { ApiError } from '@/lib/api';
import type { Locale } from '@/lib/i18n/locales';
import { libraryAnalyze, libraryShare, type LibraryAnalysis, type LibrarySnapshot } from '@/lib/library/api';
import { libraryCopy } from '@/lib/library/copy';
import { libraryError } from '@/lib/library/presentation';
import { baseCopy } from '@/lib/library/base';
import { recordTasteEvent } from '@/lib/taste/api';
import LibraryCards from './LibraryCards';
import LibraryRecommendations from './LibraryRecommendations';
import { useCooldown } from './useCooldown';
import styles from './library.module.css';

export default function LibraryReport({ data, locale, owner, submittedInput, onRefresh, existingShareId, onNewAnalysis }: {
  data: LibrarySnapshot; locale: Locale; owner?: LibraryAnalysis; submittedInput?: string;
  onRefresh?: (value: LibraryAnalysis) => void; existingShareId?: string;
  onNewAnalysis?: () => void;
}) {
  const c = libraryCopy[locale];
  const b = baseCopy[locale];
  const [confirmation, setConfirmation] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [status, setStatus] = useState('');
  const [error, setError] = useState('');
  const [shareUrl, setShareUrl] = useState('');
  const [image, setImage] = useState<{ url: string; index?: number; width: number; height: number } | null>(null);
  const shareId = useRef(existingShareId);
  const latestAnalysis = useRef(owner);
  const lock = useRef(false);
  const cooldown = useCooldown();

  async function save(index?: number) {
    if (saving) return;
    setSaving(true); setError('');
    try {
      const { saveLibraryImage, libraryImageSize } = await import('@/lib/library/export');
      const url = await saveLibraryImage(data, locale, index);
      setImage({ url, index, ...libraryImageSize(data, locale, index) });
      recordTasteEvent('SHARE_CLICK', 'LIBRARY', 'IMAGE'); setStatus(c.imageSaved);
    } catch { setError(c.imageError); }
    finally { setSaving(false); }
  }

  async function share() {
    if (lock.current || cooldown.remaining) return;
    lock.current = true; setSharing(true); setError(''); setStatus(c.sharing);
    try {
      if (!shareId.current) {
        let analysis = latestAnalysis.current;
        if (!analysis) throw new Error('Missing analysis');
        try { shareId.current = (await libraryShare(analysis.analysisId)).shareId; }
        catch (cause) {
          if (!(cause instanceof ApiError) || cause.code !== 'ANALYSIS_EXPIRED' || !submittedInput) throw cause;
          setStatus(c.expireRecovery);
          analysis = await libraryAnalyze(submittedInput, locale);
          latestAnalysis.current = analysis;
          onRefresh?.(analysis);
          shareId.current = (await libraryShare(analysis.analysisId)).shareId;
        }
      }
      const url = `${window.location.origin}/${locale}/library/share/${shareId.current}?s=1`;
      setShareUrl(url); setConfirmation(false);
      recordTasteEvent('SHARE_CLICK', 'LIBRARY', 'LINK');
      try { await navigator.clipboard.writeText(url); setStatus(c.copied); }
      catch { setStatus(c.manual); }
    } catch (cause) { setError(libraryError(cause, locale)); setStatus(''); cooldown.fromError(cause); }
    finally { lock.current = false; setSharing(false); }
  }

  return <>
    {data.limitedData ? <p className={styles.limited}>{c.limited}</p> : null}
    <p className={styles.hint}>{c.coverage} ({data.stats.matchedCount} / {data.stats.ownedCount})</p>
    <LibraryCards data={data} locale={locale} onSave={index => void save(index)} saving={saving} />
    <div className={styles.actions}>
      <button type="button" className={styles.button} disabled={sharing || cooldown.remaining > 0} onClick={() => { if (shareId.current) void share(); else setConfirmation(true); }}>{sharing ? c.sharing : b.share}</button>
      <button type="button" className={styles.secondary} disabled={saving} onClick={() => void save()}>{b.saveAll}</button>
      {existingShareId ? <Link href={`/${locale}/library`}>{c.self}</Link> : <button type="button" className={styles.secondary} onClick={onNewAnalysis}>{c.other}</button>}
    </div>
    {confirmation ? <section className={styles.sharePanel} aria-label={c.share}>
      <p>{c.privacy}</p><div>
        <button type="button" className={styles.button} disabled={sharing || cooldown.remaining > 0} onClick={() => void share()}>{c.confirm}</button>
        <button type="button" className={styles.secondary} disabled={sharing} onClick={() => setConfirmation(false)}>{c.cancel}</button>
      </div>
    </section> : null}
    <p role="status" className={styles.status}>{status}{cooldown.remaining ? ` ${cooldown.remaining} ${c.wait}` : ''}</p>
    {shareUrl ? <label>{c.shareUrl}<input className={styles.shareUrl} readOnly value={shareUrl} onFocus={event => event.target.select()} /></label> : null}
    {image ? <details className={styles.imagePreview} open>
      <summary>{c.imagePreview}</summary>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={image.url} alt={c.imagePreview} width={image.width} height={image.height} />
      <a className={styles.secondary} href={image.url} download={`playalike-base-${image.index === undefined ? 'overview' : image.index + 1}.png`}>{c.download}</a>
    </details> : null}
    {error ? <p role="alert" className={styles.error}>{error}</p> : null}
    {owner?.profile.steamId64 ? <LibraryRecommendations key={owner.profile.steamId64} steamId={owner.profile.steamId64} locale={locale} /> : null}
  </>;
}
