"use client";

import { useRef, useState } from 'react';
import type { Locale } from '@/lib/i18n/locales';
import type { LibrarySnapshot, PlayedGame } from '@/lib/library/api';
import { baseCopy, baseTheme, genreName, libraryCompanion } from '@/lib/library/base';
import { libraryCopy } from '@/lib/library/copy';
import { fillLibraryText, hoursText, numberText, percentText } from '@/lib/library/presentation';
import styles from './base.module.css';
import './base-tokens.css';
import CollectionChest from './CollectionChest';
import StationEmblem, { type StationKind } from './StationEmblem';
import TastePortrait from '../taste/TastePortrait';
import { getTasteType } from '@/lib/taste/catalog';

function Memory({ game, locale, rank }: { game: PlayedGame; locale: Locale; rank: number }) {
  const [failed, setFailed] = useState(false);
  return <a className={styles.memory} href={`https://store.steampowered.com/app/${game.appid}/?l=${locale === 'ko' ? 'korean' : 'english'}`} target="_blank" rel="noopener noreferrer">
    <div className={styles.memoryArt}>
      {!failed ? (
        // Steam cover art is decorative; titles remain readable if the image is unavailable.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={`https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${game.appid}/header.jpg`} width={460} height={215} alt="" loading="lazy" onError={() => setFailed(true)} />
      ) : <StationEmblem kind="gallery" />}
      <span>0{rank + 1}</span>
    </div>
    <strong>{game.name || `App ${game.appid}`}</strong>
    <p>{hoursText(game.hours, locale)} {libraryCopy[locale].hourUnit} <span>{percentText(game.ratio, locale)}</span></p>
  </a>;
}

export default function LibraryBase({ data, locale, onSave, saving = false }: {
  data: LibrarySnapshot; locale: Locale; onSave?: (index: number) => void; saving?: boolean;
}) {
  const [selected, setSelected] = useState(0);
  const tabs = useRef<(HTMLButtonElement | null)[]>([]);
  const theme = baseTheme(data.genreProfile);
  const companion = libraryCompanion(data.genreProfile);
  const character = getTasteType(companion.code)!;
  const ko = locale === 'ko';
  const b = baseCopy[locale];
  const c = libraryCopy[locale];
  const stations = [b.warehouse, b.gallery, b.attributes, b.map];
  const icons: StationKind[] = ['warehouse', 'gallery', 'attributes', 'map'];
  const h = (value: number) => `${hoursText(value, locale)} ${c.hourUnit}`;
  const memories = data.diagnosis.concentratedGames.length ? data.diagnosis.concentratedGames : data.stats.mostPlayed ? [data.stats.mostPlayed] : [];
  const npc = b.npc[data.verdict.crime.code] ?? b.npc.DEFAULT;

  return <section className={styles.base} data-theme={theme.id} aria-label={theme[locale]}>
    <div className={styles.scene}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className={styles.backdrop} src={theme.image} width={1672} height={941} alt="" fetchPriority="high" />
      <header className={styles.sceneHeading}><p>{b.eyebrow}</p><h2>{theme[locale]}</h2><span>{theme.mixed ? b.mixed : b.welcome}</span></header>
      <div className={styles.hud}><div><span>{c.owned}</span><strong>{numberText(data.stats.ownedCount, locale)}</strong></div><div><span>{c.hours}</span><strong>{h(data.stats.totalHours)}</strong></div></div>
      <div className={styles.stations} role="tablist" aria-label={b.stations}>
        {stations.map((name, index) => <button key={name} ref={element => { tabs.current[index] = element; }} type="button" role="tab" id={`base-tab-${index}`} aria-controls="base-panel" aria-selected={selected === index} tabIndex={selected === index ? 0 : -1} className={`${styles.station} ${selected === index ? styles.active : ''}`} onClick={() => setSelected(index)} onKeyDown={event => {
          let next: number | undefined;
          if (event.key === 'ArrowRight' || event.key === 'ArrowDown') next = (index + 1) % 4;
          if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') next = (index + 3) % 4;
          if (event.key === 'Home') next = 0; if (event.key === 'End') next = 3;
          if (next !== undefined) { event.preventDefault(); setSelected(next); tabs.current[next]?.focus(); }
        }}>
          <StationEmblem kind={icons[index]} /><span>{name}</span><small>{b.enter}</small>
        </button>)}
      </div>
    </div>
    <div className={styles.sceneFooter}><p>{b.guide}</p>{theme.prop === 'puzzle' || theme.prop === 'books' ? <span aria-label={theme.prop === 'puzzle' ? b.puzzleProp : b.booksProp}>{theme.prop === 'puzzle' ? b.puzzleProp : b.booksProp}</span> : null}{theme.insufficient ? <p>{b.insufficient}</p> : null}{theme.secondary ? <span>{b.secondary} · {theme.secondary[locale]}</span> : null}</div>

    <section id="base-panel" className={styles.panel} role="tabpanel" aria-labelledby={`base-tab-${selected}`} tabIndex={0}>
      <header className={styles.panelHeading}><div><p>PLAYER RECORD / 0{selected + 1}</p><h2>{stations[selected]}</h2></div>{onSave ? <button className={styles.save} disabled={saving} type="button" onClick={() => onSave(selected)}>{b.save} ↓</button> : null}</header>
      {selected === 0 ? <div className={styles.storage}>
        <div className={styles.crateDisplay} aria-hidden="true"><CollectionChest /><span>× {numberText(data.stats.ownedCount, locale)}</span></div>
        <div><p className={styles.classification}>{c.ledgerNames[data.ledger.code] ?? c.unknown}</p>
          <dl className={styles.numbers}><div><dt>{b.collected}</dt><dd>{numberText(data.stats.ownedCount, locale)} <small>{c.games}</small></dd></div><div><dt>{b.sleeping}</dt><dd>{numberText(data.stats.zeroPlayCount, locale)} <small>{c.games}</small></dd></div><div><dt>{c.recent}</dt><dd>{h(data.stats.recentHours)}</dd></div><div><dt>{c.unplayed}</dt><dd>{percentText(data.stats.zeroPlayRatio, locale)}</dd></div></dl>
          <progress max={1} value={data.stats.zeroPlayRatio} aria-label={c.unplayed} /><p className={styles.reading}>{fillLibraryText(data.ledger)}</p>
        </div></div> : null}
      {selected === 1 ? <><p className={styles.help}>{b.visits}</p><div className={styles.memories}>{memories.map((game, index) => <Memory game={game} key={game.appid} locale={locale} rank={index} />)}</div>{!memories.length ? <p>{b.noVisits}</p> : null}</> : null}
      {selected === 2 ? <div className={styles.characterSheet}>
        <div className={styles.equipment}>
          <p className={styles.companionLabel}>{ko ? '취향의 동행자' : 'Your taste companion'}</p>
          <TastePortrait key={companion.code} code={companion.code} name={character.name[locale]} />
          <strong>{character.name[locale]}</strong>
          <p>{companion.genre ? genreName(companion.genre, locale) : ko ? '함께 떠나는 여행자' : 'A fellow traveler'}</p>
          <small>{ko ? '플레이 장르의 분위기에 맞춘 동행자예요. 테스트 유형 판정은 아니에요.' : 'A companion chosen for your genre’s atmosphere, not a scored test type.'}</small>
        </div>
        <div className={styles.attributes}>{data.gamerStats.map(stat => <div key={stat.code} className={styles.attribute}><div><span>{stat.label}</span><strong>{stat.value === null ? c.insufficient : `${stat.value} / 100`}</strong></div>{stat.value === null ? <p className={styles.missing}>—</p> : <meter min={0} max={100} value={stat.value} aria-label={stat.label} />}</div>)}<p className={styles.help}>{c.statsNote}</p></div>
      </div> : null}
      {selected === 3 ? <div className={styles.mapLayout}><div><h3>{b.genreMap}</h3>
        {data.genreProfile?.distribution.length ? <><ol className={styles.genreList}>{data.genreProfile.distribution.map(genre => <li key={genre.code}><div><span>{genreName(genre.code, locale)}</span><strong>{percentText(genre.ratio, locale)}</strong></div><progress max={1} value={genre.ratio} aria-label={genreName(genre.code, locale)} /></li>)}</ol><p className={styles.help}>{b.basis}</p><p className={styles.coverage}>{b.coverage}: {percentText(data.genreProfile.coverageRatio, locale)}</p></> : <p className={styles.help}>{b.noGenres}</p>}
      </div><div className={styles.discoveries}><StationEmblem kind="map" /><h3>{b.glimpse}</h3><p><span>{c.favorite}</span><strong>{data.diagnosis.favoriteTag ?? c.insufficient}</strong></p><p><span>{c.hidden}</span><strong>{data.diagnosis.hiddenTaste ?? c.insufficient}</strong></p><ul className={styles.tags}>{data.stats.topTags.map(tag => <li key={tag.tag}>{tag.tag} <strong>{percentText(tag.ratio, locale)}</strong></li>)}</ul></div></div> : null}
    </section>
    <aside className={styles.npc}><span aria-hidden="true">◆</span><div><strong>{b.archive}</strong><p>{npc}</p></div></aside>
  </section>;
}
