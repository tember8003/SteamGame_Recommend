"use client";
import { useEffect, useRef, useState } from 'react';
import GameCard from '../GameCard';
import { ApiError } from '@/lib/api';
import type { Locale } from '@/lib/i18n/locales';
import type { ExplanationItem, SteamApp } from '@/lib/types';
import { tasteRecommend, tasteErrorText, type TasteResult, type TypeView } from '@/lib/taste/api';
import styles from './taste.module.css';

export default function TasteGames({ view, result, locale }: { view: TypeView; result?: TasteResult; locale: Locale }) {
  const ko = locale === 'ko';
  const [games, setGames] = useState<{ game: SteamApp; items: ExplanationItem[] }[]>(() => view.featuredGames.map(game => ({ game, items: [] })));
  const [pending, setPending] = useState(false);
  const [exhausted, setExhausted] = useState(false);
  const [error, setError] = useState('');
  const [cooldown, setCooldown] = useState(0);
  const lock = useRef(false);
  const excluded = useRef(view.featuredGames.map(game => game.appid));
  useEffect(() => {
    if (!cooldown) return;
    const timer = setTimeout(() => setCooldown(value => Math.max(0, value - 1)), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);
  async function more() {
    if (lock.current || cooldown || exhausted) return;
    lock.current = true; setPending(true); setError('');
    try {
      const page = await tasteRecommend(view.code, excluded.current);
      setGames(current => {
        const seen = new Set(current.map(item => item.game.appid));
        return [...current, ...page.games.filter(game => { if (seen.has(game.appid)) return false; seen.add(game.appid); return true }).map(game => ({ game, items: page.explanations.find(item => item.appid === game.appid)?.items ?? [] }))];
      });
      excluded.current = [...new Set([...excluded.current, ...page.games.map(game => game.appid)])].slice(-500);
    } catch (error) {
      if (error instanceof ApiError && error.status === 404 && error.code === 'TASTE_NO_MORE_GAMES') setExhausted(true);
      else { setError(tasteErrorText(error, locale)); if (error instanceof ApiError && error.status === 429) setCooldown(Math.ceil(error.retryAfterSeconds ?? 30)) }
    } finally { lock.current = false; setPending(false) }
  }
  return <section className={styles.gameSection} aria-label={ko ? '이 취향에 맞는 게임' : 'Games for this play style'} aria-busy={pending}>
    {games.length ? <><h2>{ko ? '이런 게임은 어때요?' : 'Your next adventure'}</h2><div className={styles.gameGrid}>
      {games.map(({ game, items }, rank) => <GameCard key={game.appid} game={game} explanation={{ reasons: items, caveats: [] }}
        feedback={result ? { context: 'TASTE', rank } : undefined} />)}
    </div></> : null}
    {error ? <p role="alert" className={styles.apiError}>{error}</p> : null}
    {exhausted ? <p role="status">{ko ? '지금 보여드릴 게임을 모두 살펴봤어요.' : 'You have seen all available games for now.'}</p> :
      <button className={styles.primaryButton} disabled={pending || cooldown > 0} onClick={more}>{pending ? (ko ? '게임을 찾고 있어요…' : 'Finding games…') : cooldown ? `${cooldown}s` : (ko ? '다른 게임 더 추천받기' : 'Find more games')}</button>}
  </section>;
}
