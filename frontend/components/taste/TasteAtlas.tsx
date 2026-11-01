"use client";

import Link from 'next/link';
import { useState } from 'react';
import type { Locale } from '@/lib/i18n/locales';
import { filterTasteTypes, tasteAxes, tasteTypes, type TasteType } from '@/lib/taste/catalog';
import TastePortrait from './TastePortrait';
import styles from './taste.module.css';

export default function TasteAtlas({ locale, types: source = tasteTypes }: { locale: Locale; types?: readonly TasteType[] }) {
  const ko = locale === 'ko';
  const [query, setQuery] = useState('');
  const [side, setSide] = useState('');
  const types = filterTasteTypes(query, side, locale, source);
  function reset() { setQuery(''); setSide(''); }

  return <main id="taste-main" className={styles.main}>
    <nav className={styles.breadcrumb} aria-label={ko ? '현재 위치' : 'Breadcrumb'}>
      <Link href={`/${locale}`}>{ko ? '마을로 돌아가기' : 'Back to the village'}</Link>
      <span aria-hidden="true">/</span><span>{ko ? '게이머 도감' : 'Player atlas'}</span>
    </nav>
    <header className={styles.intro}>
      <div><h1>{ko ? '당신은 어떤 게임 속 인물인가요?' : 'Meet your player character'}</h1>
        <p>{ko ? '익숙한 캐릭터로 만나는 16가지 플레이 취향.' : 'Sixteen ways to play. A familiar face for each.'}</p></div>
      <Link href={`/${locale}/taste/test`} className={styles.primaryButton}>{ko ? '내 유형 찾기' : 'Find my type'}</Link>
    </header>

    <section className={styles.controls} aria-label={ko ? '유형 찾아보기' : 'Find a type'}>
      <label className={styles.searchLabel}>
        <span>{ko ? '캐릭터, 게임, 유형 검색' : 'Search characters, games or types'}</span>
        <input type="search" value={query} onChange={event => setQuery(event.target.value)}
          placeholder={ko ? '예: 조야, 오버워치, GMHS' : 'Try Zoya, Overwatch, GMHS'} />
      </label>
      <div className={styles.filters} role="group" aria-label={ko ? '성향 필터' : 'Play style filters'}>
        <button type="button" aria-pressed={!side} onClick={() => setSide('')}>{ko ? '모두' : 'All'}</button>
        {tasteAxes.flat().map(axis => <button key={axis.code} type="button" aria-pressed={side === axis.code}
          onClick={() => setSide(side === axis.code ? '' : axis.code)}>{axis[locale]}</button>)}
      </div>
    </section>
    <p className={styles.resultCount} role="status">{ko ? `${types.length}명의 캐릭터를 만날 수 있어요` : `${types.length} characters to meet`}</p>

    {types.length ? <ul className={styles.grid}>
      {types.map((type, index) => <li key={type.code}>
        <Link href={`/${locale}/taste/${type.code}`} className={styles.card}
          aria-label={`${type.name[locale]} ${type.code} ${ko ? '유형 보기' : 'View type'}`}>
          <div className={styles.cardTop}><span>{type.code}</span><span className={styles.cardNumber}>{String(index + 1).padStart(2, '0')}</span></div>
          <TastePortrait code={type.code} name={type.name[locale]} />
          <div className={styles.cardCopy}>
            <p className={styles.work}>{type.work[locale]}</p>
            <h2>{type.name[locale]}</h2>
            <p className={styles.traits}>{type.traits[locale]}</p>
            <span className={styles.cardAction}>{ko ? '플레이 취향 펼쳐보기' : 'Discover this play style'}<span aria-hidden="true">＋</span></span>
          </div>
        </Link>
      </li>)}
    </ul> : <div className={styles.empty}>
      <h2>{ko ? '찾는 캐릭터가 없어요.' : 'No characters found.'}</h2>
      <p>{ko ? '다른 이름이나 게임으로 찾아보세요.' : 'Try another name or game.'}</p>
      <button type="button" className={styles.primaryButton} onClick={reset}>{ko ? '검색 초기화' : 'Reset search'}</button>
    </div>}

    <footer className={styles.footer}>
      <p>{ko ? '캐릭터에 빗댄 비공식 취향 도감입니다. 캐릭터의 공식 성격 유형을 뜻하지 않습니다.' : 'An unofficial play-style atlas inspired by game characters, not their canonical personality types.'}</p>
      <p>{ko ? '캐릭터와 작품의 권리는 각 권리자에게 있으며, Playalike와 제휴하거나 후원하지 않습니다.' : 'Characters and games belong to their respective owners. No affiliation or endorsement is implied.'}</p>
    </footer>
  </main>;
}
