"use client";
import { useRef, useState } from 'react';
import { recommendProfile } from '@/lib/api';
import { toExcludeAppids, mergeShownAppids } from '@/lib/reroll';
import type { Locale } from '@/lib/i18n/locales';
import type { RecommendationResult } from '@/lib/types';
import { libraryCopy } from '@/lib/library/copy';
import RecommendResults from '@/components/RecommendResults';
import styles from './library.module.css';

export default function LibraryRecommendations({ steamId, locale }: { steamId: string; locale: Locale }) {
  const c = libraryCopy[locale];
  const [result, setResult] = useState<RecommendationResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const shown = useRef<number[]>([]);
  const locked = useRef(false);
  async function recommend() {
    if (locked.current) return;
    locked.current = true; setLoading(true); setError(false);
    try {
      const value = await recommendProfile(steamId, { review: 1000, koreanCheck: false, freeCheck: false, excludeAppids: toExcludeAppids(shown.current) });
      shown.current = mergeShownAppids(shown.current, value.recommendedGame);
      setResult(value);
    } catch { setError(true); }
    finally { locked.current = false; setLoading(false); }
  }
  return <section className={styles.recommendations} aria-labelledby="library-recommendations" aria-busy={loading}>
    <h2 id="library-recommendations">{c.recommendation}</h2><p>{c.recommendationHint}</p>
    {result ? <RecommendResults games={result.recommendedGame} usedTags={result.usedTags} reviews={result.review} context="PROFILE" steamId={steamId} reviewOptions={{ showHeader: true, showHours: false, showRatingBar: true, showReviewText: false }} onRecommendAgain={() => void recommend()} recommendAgainDisabled={loading} /> : <button className={styles.button} disabled={loading} onClick={() => void recommend()}>{loading ? c.loading : c.recommend}</button>}
    {result?.recommendedGame.length === 0 ? <p>{c.empty}</p> : null}
    {error ? <p role="alert" className={styles.error}>{c.recommendError}</p> : null}
  </section>;
}
