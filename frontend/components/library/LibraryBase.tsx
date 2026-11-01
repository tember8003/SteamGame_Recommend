"use client";

import { useRef, useState } from 'react';
import type { Locale } from '@/lib/i18n/locales';
import type { LibrarySnapshot, PlayedGame } from '@/lib/library/api';
import { baseCopy, baseTheme, genreName } from '@/lib/library/base';
import { libraryCopy } from '@/lib/library/copy';
import { fillLibraryText, hoursText, numberText, percentText } from '@/lib/library/presentation';
import styles from './base.module.css';

function PixelProp({ kind }: { kind: string }) {
  const art: Record<string, React.ReactNode> = {
    warehouse: <><path d="M4 13h24v16H4zM8 5h16v8H8z" fill="#a77943"/><path d="M4 17h24M8 5v24m16-24v24M12 13l12 16M8 29l16-16" stroke="#e5c78a" strokeWidth="2"/></>,
    gallery: <><path d="M9 3h14v12l-5 5v6h7v4H7v-4h7v-6l-5-5z" fill="#e1bd69"/><path d="M9 6H3v8l6 4m14-12h6v8l-6 4" fill="none" stroke="#a88848" strokeWidth="3"/></>,
    attributes: <><path d="M5 4h22v14L16 30 5 18z" fill="#82b0a3"/><path d="M16 7v17M9 14h14" stroke="#e5d9b8" strokeWidth="3"/></>,
    map: <><path d="m2 7 9-4 10 4 9-4v23l-9 4-10-4-9 4z" fill="#d4bf8f"/><path d="M11 3v23M21 7v23" stroke="#9e845a" strokeWidth="2"/><path d="m6 21 6-8 6 6 7-9" fill="none" stroke="#7c553d" strokeWidth="2"/></>,
    books: <><path d="M3 22h25v7H3zM7 13h22v7H7zM3 4h22v7H3z" fill="#9a7969"/><path d="M8 5v5m4 4v5M8 23v5" stroke="#e4d4ac" strokeWidth="3"/></>,
    puzzle: <><path d="M3 4h10v6h6V4h10v10h-6v6h6v9H19v-6h-6v6H3v-9h6v-6H3z" fill="#bca1c4"/><path d="M13 10h6v13h-6z" fill="#d9c581"/></>,
    chair: <><path d="M6 3h20v16H6zM4 19h24v6H4zM7 25v6m18-6v6" fill="#a17a4e" stroke="#d7bd84" strokeWidth="2"/></>,
    compass: <><path d="M6 6h20v20H6z" fill="#b89c65"/><path d="m16 4 7 19-7-4-7 4z" fill="#e3d2a0"/><path d="m16 9 3 9-3-2-3 2z" fill="#a86450"/></>,
    chess: <><path d="M12 3h8v8h4v7h-4v7h7v5H5v-5h7v-7H8v-7h4z" fill="#b7c5b6"/></>,
    target: <><path d="M4 4h24v24H4z" fill="#be8068"/><path d="M8 8h16v16H8z" fill="#e3cc9e"/><path d="M12 12h8v8h-8z" fill="#be8068"/></>,
    tools: <><path d="m4 4 24 24M26 4 4 26" stroke="#e3cc9e" strokeWidth="5"/><path d="M2 2h10v7H2zm18 0h10v8H20z" fill="#a5b7b4"/></>,
    plant: <><path d="M9 20h14l-2 11H11z" fill="#bf8864"/><path d="M15 9h3v12h-3zM3 6h12v9H9zM18 3h12v9h-6z" fill="#8fb987"/></>,
    candle: <><path d="M12 12h9v16h-9zM7 28h20v4H7z" fill="#e1c896"/><path d="m17 1 6 7-6 6-5-6z" fill="#e0a359"/></>,
    trophy: <><path d="M9 3h14v13l-5 5v5h8v5H6v-5h8v-5l-5-5z" fill="#e1bd69"/></>,
  };
  return <svg viewBox="0 0 32 32" aria-hidden="true" shapeRendering="crispEdges">{art[kind] ?? art.gallery}</svg>;
}

function Memory({ game, locale, rank }: { game: PlayedGame; locale: Locale; rank: number }) {
  const [failed, setFailed] = useState(false);
  return <a className={styles.memory} href={`https://store.steampowered.com/app/${game.appid}/?l=${locale === 'ko' ? 'korean' : 'english'}`} target="_blank" rel="noopener noreferrer">
    <div className={styles.memoryArt}>
      {!failed ? (
        // Steam cover art is decorative; titles remain readable if the image is unavailable.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={`https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${game.appid}/header.jpg`} width={460} height={215} alt="" loading="lazy" onError={() => setFailed(true)} />
      ) : <PixelProp kind="gallery" />}
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
  const b = baseCopy[locale];
  const c = libraryCopy[locale];
  const stations = [b.warehouse, b.gallery, b.attributes, b.map];
  const icons = ['warehouse', 'gallery', 'attributes', 'map'];
  const h = (value: number) => `${hoursText(value, locale)} ${c.hourUnit}`;
  const memories = data.diagnosis.concentratedGames.length ? data.diagnosis.concentratedGames : data.stats.mostPlayed ? [data.stats.mostPlayed] : [];
  const npc = b.npc[data.verdict.crime.code] ?? b.npc.DEFAULT;
  const hasCoop = data.gamerStats.some(stat => stat.code === 'COOP' && stat.value !== null && stat.value > 0);

  return <section className={styles.base} data-theme={theme.id} aria-label={theme[locale]}>
    <div className={styles.scene}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className={styles.backdrop} src={theme.image} width={1672} height={941} alt="" fetchPriority="high" />
      <header className={styles.sceneHeading}><p>{b.eyebrow}</p><h2>{theme[locale]}</h2><span>{theme.mixed ? b.mixed : b.welcome}</span></header>
      <div className={styles.hud}><div><span>{c.owned}</span><strong>{numberText(data.stats.ownedCount, locale)}</strong></div><div><span>{c.hours}</span><strong>{h(data.stats.totalHours)}</strong></div></div>
      <div className={styles.props} aria-label={theme.prop === 'puzzle' ? b.puzzleProp : theme.prop === 'books' ? b.booksProp : theme.secondary ? `${b.secondary}: ${theme.secondary[locale]}` : undefined}>
        {theme.prop === 'puzzle' || theme.prop === 'books' ? <span className={styles.extraProp}><PixelProp kind={theme.prop} /></span> : null}
        {theme.secondary ? <span className={styles.secondaryProp} title={`${b.secondary}: ${theme.secondary[locale]}`}><PixelProp kind={theme.secondary.prop} /></span> : null}
        {hasCoop ? <span className={styles.coopChair} title={b.coopProp}><PixelProp kind="chair" /></span> : null}
      </div>
      <div className={styles.stations} role="tablist" aria-label={b.stations}>
        {stations.map((name, index) => <button key={name} ref={element => { tabs.current[index] = element; }} type="button" role="tab" id={`base-tab-${index}`} aria-controls="base-panel" aria-selected={selected === index} tabIndex={selected === index ? 0 : -1} className={`${styles.station} ${selected === index ? styles.active : ''}`} onClick={() => setSelected(index)} onKeyDown={event => {
          let next: number | undefined;
          if (event.key === 'ArrowRight' || event.key === 'ArrowDown') next = (index + 1) % 4;
          if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') next = (index + 3) % 4;
          if (event.key === 'Home') next = 0; if (event.key === 'End') next = 3;
          if (next !== undefined) { event.preventDefault(); setSelected(next); tabs.current[next]?.focus(); }
        }}>
          <PixelProp kind={icons[index]} /><span>{name}</span><small>{b.enter} →</small>
        </button>)}
      </div>
    </div>
    <div className={styles.sceneFooter}><p>{b.guide}</p>{theme.insufficient ? <p>{b.insufficient}</p> : null}{theme.secondary ? <span>{b.secondary} · {theme.secondary[locale]}</span> : null}</div>

    <section id="base-panel" className={styles.panel} role="tabpanel" aria-labelledby={`base-tab-${selected}`} tabIndex={0}>
      <header className={styles.panelHeading}><div><p>PLAYER RECORD / 0{selected + 1}</p><h2>{stations[selected]}</h2></div>{onSave ? <button className={styles.save} disabled={saving} type="button" onClick={() => onSave(selected)}>{b.save} ↓</button> : null}</header>
      {selected === 0 ? <div className={styles.storage}>
        <div className={styles.crateDisplay} aria-hidden="true"><PixelProp kind="warehouse" /><span>× {numberText(data.stats.ownedCount, locale)}</span></div>
        <div><p className={styles.classification}>{c.ledgerNames[data.ledger.code] ?? c.unknown}</p>
          <dl className={styles.numbers}><div><dt>{b.collected}</dt><dd>{numberText(data.stats.ownedCount, locale)} <small>{c.games}</small></dd></div><div><dt>{b.sleeping}</dt><dd>{numberText(data.stats.zeroPlayCount, locale)} <small>{c.games}</small></dd></div><div><dt>{c.recent}</dt><dd>{h(data.stats.recentHours)}</dd></div><div><dt>{c.unplayed}</dt><dd>{percentText(data.stats.zeroPlayRatio, locale)}</dd></div></dl>
          <progress max={1} value={data.stats.zeroPlayRatio} aria-label={c.unplayed} /><p className={styles.reading}>{fillLibraryText(data.ledger)}</p>
        </div></div> : null}
      {selected === 1 ? <><p className={styles.help}>{b.visits}</p><div className={styles.memories}>{memories.map((game, index) => <Memory game={game} key={game.appid} locale={locale} rank={index} />)}</div>{!memories.length ? <p>{b.noVisits}</p> : null}</> : null}
      {selected === 2 ? <div className={styles.characterSheet}>
        <div className={styles.equipment} aria-hidden="true"><PixelProp kind="attributes" /><p>PLAYER<br />STATUS</p></div>
        <div className={styles.attributes}>{data.gamerStats.map(stat => <div key={stat.code} className={styles.attribute}><div><span>{stat.label}</span><strong>{stat.value === null ? c.insufficient : `${stat.value} / 100`}</strong></div>{stat.value === null ? <p className={styles.missing}>—</p> : <meter min={0} max={100} value={stat.value} aria-label={stat.label} />}</div>)}<p className={styles.help}>{c.statsNote}</p></div>
      </div> : null}
      {selected === 3 ? <div className={styles.mapLayout}><div><h3>{b.genreMap}</h3>
        {data.genreProfile?.distribution.length ? <><ol className={styles.genreList}>{data.genreProfile.distribution.map(genre => <li key={genre.code}><div><span>{genreName(genre.code, locale)}</span><strong>{percentText(genre.ratio, locale)}</strong></div><progress max={1} value={genre.ratio} aria-label={genreName(genre.code, locale)} /></li>)}</ol><p className={styles.help}>{b.basis}</p><p className={styles.coverage}>{b.coverage}: {percentText(data.genreProfile.coverageRatio, locale)}</p></> : <p className={styles.help}>{b.noGenres}</p>}
      </div><div className={styles.discoveries}><PixelProp kind="map" /><h3>{b.glimpse}</h3><p><span>{c.favorite}</span><strong>{data.diagnosis.favoriteTag ?? c.insufficient}</strong></p><p><span>{c.hidden}</span><strong>{data.diagnosis.hiddenTaste ?? c.insufficient}</strong></p><ul className={styles.tags}>{data.stats.topTags.map(tag => <li key={tag.tag}>{tag.tag} <strong>{percentText(tag.ratio, locale)}</strong></li>)}</ul></div></div> : null}
    </section>
    <aside className={styles.npc}><span aria-hidden="true">◆</span><div><strong>{b.archive}</strong><p>{npc}</p></div></aside>
  </section>;
}
