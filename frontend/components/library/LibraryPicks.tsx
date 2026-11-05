"use client";

import { useState } from 'react';
import type { LibraryPicks as Picks } from '@/lib/library/api';
import type { Locale } from '@/lib/i18n/locales';
import { picksCopy, matchedReason, idleReason, pickedGenreName, briefVisits } from '@/lib/library/picks';
import { hoursText } from '@/lib/library/presentation';
import { libraryCopy } from '@/lib/library/copy';
import StationEmblem from './StationEmblem';
import styles from './picks.module.css';

function PickCover({ appid }: { appid: number }) {
  const [failed, setFailed] = useState(false);
  return <div className={styles.cover} aria-hidden="true">{failed ? <StationEmblem kind="warehouse" /> :
    // Decorative Steam cover; the linked title remains readable on failure.
    // eslint-disable-next-line @next/next/no-img-element
    <img src={`https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${appid}/header.jpg`} alt="" width={460} height={215} loading="lazy" onError={() => setFailed(true)} />
  }</div>;
}

export function GamePicks({ picks, kind, locale }: { picks?: Picks | null; kind: 'playNext' | 'revisit'; locale: Locale }) {
  const games = picks?.[kind];
  if (!games?.length) return null;
  const c = picksCopy[locale];
  return <section className={styles.picks} aria-label={c[kind]}>
    <h3>{c[kind]}</h3><p className={styles.hint}>{kind === 'playNext' ? c.nextHint : c.revisitHint}</p>
    <ul className={styles.games}>{games.map(game => <li key={game.appid}>
      <a href={`https://store.steampowered.com/app/${game.appid}/?l=${locale === 'ko' ? 'korean' : 'english'}`} target="_blank" rel="noopener noreferrer">
        <PickCover key={game.appid} appid={game.appid} />
        <div className={styles.detail}><strong>{game.name || `App ${game.appid}`}</strong>
          {'matchedTags' in game ? <p>{matchedReason(game.matchedTags, locale)}</p> : <>
            <span>{hoursText(game.hours, locale)} {libraryCopy[locale].hourUnit}</span><p>{idleReason(game.idleDays, locale)}</p>
          </>}
        </div>
      </a>
    </li>)}</ul>
  </section>;
}

export function AbandonedGenre({ picks, locale }: { picks?: Picks | null; locale: Locale }) {
  const genre = picks?.abandonedGenre;
  if (!genre) return null;
  const c = picksCopy[locale];
  return <section className={styles.abandoned} aria-label={c.abandoned}>
    <h3>{c.abandoned}</h3><strong>{pickedGenreName(genre.genre, locale)}</strong>
    <p>{briefVisits(genre.gameCount, locale)}</p>
    {genre.games.length ? <><p className={styles.label}>{c.examples}</p><ul>{genre.games.map((name, index) => <li key={`${index}-${name}`}>{name}</li>)}</ul></> : null}
    <p className={styles.hint}>{c.abandonedHint}</p>
  </section>;
}
