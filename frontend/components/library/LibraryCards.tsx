"use client";

import type { Locale } from '@/lib/i18n/locales';
import type { LibrarySnapshot } from '@/lib/library/api';
import { libraryCopy } from '@/lib/library/copy';
import { fillLibraryText, hoursText, numberText, percentText } from '@/lib/library/presentation';
import styles from './library.module.css';

export default function LibraryCards({ data, locale, onSave, saving = false }: {
  data: LibrarySnapshot; locale: Locale; onSave?: (index: number) => void; saving?: boolean;
}) {
  const c = libraryCopy[locale];
  const { stats, diagnosis } = data;
  const h = (value: number) => `${hoursText(value, locale)} ${c.hourUnit}`;
  const title = (index: number, name: string, symbol: string) => <header className={styles.cardHeader}>
    <span className={styles.documentNumber}>ARCHIVE / 0{index + 1}</span><span className={styles.symbol} aria-hidden="true">{symbol}</span>
    <h2 id={`library-card-${index}`}>{name}</h2>
  </header>;
  const save = (index: number) => onSave ? <button type="button" className={styles.saveCard} disabled={saving} onClick={() => onSave(index)}>{c.save} ↓</button> : null;
  return <div className={styles.cards}>
    <article className={`${styles.card} ${styles.ledger}`} aria-labelledby="library-card-0">
      {title(0, c.ledger, '▤')}
      <p className={styles.classification}>{c.ledgerNames[data.ledger.code] ?? c.unknown}</p>
      <dl className={styles.ledgerNumbers}>
        <div><dt>{c.owned}</dt><dd>{numberText(stats.ownedCount, locale)} <small>{c.games}</small></dd></div>
        <div><dt>{c.unplayed}</dt><dd>{numberText(stats.zeroPlayCount, locale)} <small>{c.games}</small></dd></div>
        <div><dt>{c.hours}</dt><dd>{h(stats.totalHours)}</dd></div>
        <div><dt>{c.recent}</dt><dd>{h(stats.recentHours)} <small>/ {stats.recentGameCount} {c.games}</small></dd></div>
      </dl>
      <div className={styles.unplayed}><span>{c.unplayed}</span><strong>{percentText(stats.zeroPlayRatio, locale)}</strong><progress max={1} value={stats.zeroPlayRatio} aria-label={c.unplayed} /></div>
      <p className={styles.reading}>{fillLibraryText(data.ledger)}</p>
      {stats.mostPlayed ? <p className={styles.footnote}>{c.mostPlayed}<strong>{stats.mostPlayed.name || `App ${stats.mostPlayed.appid}`}</strong>{h(stats.mostPlayed.hours)} / {percentText(stats.mostPlayed.ratio, locale)}</p> : null}
      {save(0)}
    </article>

    <article className={`${styles.card} ${styles.verdict}`} aria-labelledby="library-card-1">
      {title(1, c.verdict, '⚖')}
      <div className={styles.charge}><p className={styles.caption}>{c.crime}</p><p>{fillLibraryText(data.verdict.crime)}</p></div>
      <div className={styles.sentence}><p className={styles.caption}>{c.sentence}</p><p>{fillLibraryText(data.verdict.sentence)}</p></div>
      <div className={styles.stamp} aria-hidden="true">{locale === 'ko' ? '판정 완료' : 'VERDICT FILED'}</div>
      <p className={styles.footnote}>{c.verdictNote}</p>
      {save(1)}
    </article>

    <article className={`${styles.card} ${styles.abilities}`} aria-labelledby="library-card-2">
      {title(2, c.abilities, '✣')}
      <p className={styles.caption}>PLAYER ATTRIBUTES</p>
      <div className={styles.attributes}>{data.gamerStats.map(stat => <div className={styles.attribute} key={stat.code}>
        <div><span>{stat.label}</span><strong>{stat.value === null ? c.insufficient : `${stat.value} / 100`}</strong></div>
        {stat.value === null ? <p className={styles.missing}>—</p> : <meter min={0} max={100} value={stat.value} aria-label={stat.label} />}
      </div>)}</div>
      <p className={styles.footnote}>{c.statsNote}</p>
      {save(2)}
    </article>

    <article className={`${styles.card} ${styles.diagnosis}`} aria-labelledby="library-card-3">
      {title(3, c.diagnosis, '✧')}
      <div className={styles.tastePair}>
        <div><p className={styles.caption}>{c.favorite}</p><strong>{diagnosis.favoriteTag ?? c.insufficient}</strong></div>
        <div><p className={styles.caption}>{c.hidden}</p><strong>{diagnosis.hiddenTaste ?? c.insufficient}</strong></div>
      </div>
      <h3 className={styles.smallTitle}>{c.concentrated}</h3>
      {diagnosis.concentratedGames.length ? <ol className={styles.gameList}>{diagnosis.concentratedGames.map(game => <li key={game.appid}>
        <span>{game.name || `App ${game.appid}`}</span><strong>{h(game.hours)}<small>{percentText(game.ratio, locale)}</small></strong>
      </li>)}</ol> : <p>{c.empty}</p>}
      <h3 className={styles.smallTitle}>{c.topTags}</h3>
      {stats.topTags.length ? <ul className={styles.tagList}>{stats.topTags.map(tag => <li key={tag.tag}><span>{tag.tag}</span><strong>{percentText(tag.ratio, locale)}</strong></li>)}</ul> : <p>{c.insufficient}</p>}
      {save(3)}
    </article>
  </div>;
}
