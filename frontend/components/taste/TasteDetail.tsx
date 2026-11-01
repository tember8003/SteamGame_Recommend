"use client";

import Link from 'next/link';
import { useState } from 'react';
import type { Locale } from '@/lib/i18n/locales';
import { tasteAxes, tasteTypes, type TasteType } from '@/lib/taste/catalog';
import TastePortrait from './TastePortrait';
import styles from './taste.module.css';

export default function TasteDetail({ type, locale }: { type: TasteType; locale: Locale }) {
  const ko = locale === 'ko';
  const [share, setShare] = useState<'idle' | 'copied' | 'manual'>('idle');
  const [shareUrl, setShareUrl] = useState('');
  const index = tasteTypes.findIndex(item => item.code === type.code);
  const previous = tasteTypes[(index + 15) % 16];
  const next = tasteTypes[(index + 1) % 16];
  async function copyLink() {
    const url = `${window.location.origin}/${locale}/taste/${type.code}`;
    setShareUrl(url);
    try {
      if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
      await navigator.clipboard.writeText(url);
      setShare('copied');
    } catch { setShare('manual'); }
  }

  return <main id="taste-main" className={styles.main}>
    <nav className={styles.breadcrumb} aria-label={ko ? '현재 위치' : 'Breadcrumb'}>
      <Link href={`/${locale}/taste`}>{ko ? '전체 도감' : 'All characters'}</Link>
      <span aria-hidden="true">/</span><span>{type.code}</span>
    </nav>
    <article className={styles.detail}>
      <div className={styles.characterStage}>
        <span className={styles.typePlaque}>{type.code}</span>
        <TastePortrait code={type.code} name={type.name[locale]} priority />
        <p>{type.work[locale]}</p>
      </div>
      <div className={styles.detailCopy}>
        <p className={styles.detailLabel}>{ko ? '이런 플레이어라면' : 'For the player who…'}</p>
        <h1>{type.name[locale]}</h1>
        <p className={styles.oneLiner}>{type.oneLiner[locale]}</p>
        <ul className={styles.axisBadges} aria-label={ko ? '네 가지 플레이 성향' : 'Four play preferences'}>
          {tasteAxes.map((axis, i) => {
            const selected = axis.find(side => side.code === type.code[i])!;
            return <li key={selected.code}><strong>{selected.code}</strong>{selected[locale]}</li>;
          })}
        </ul>
        <p className={styles.description}>{type.description[locale]}</p>
        <div className={styles.tags} aria-label={ko ? '추천 게임 태그' : 'Game tags'}>
          {type.tagWeights.slice(0, 4).map(tag => <span key={tag.tag}>{tag.tag}</span>)}
        </div>
        <div className={styles.detailActions}>
          <button type="button" className={styles.primaryButton} onClick={copyLink}>{ko ? '이 유형 링크 복사' : 'Copy type link'}</button>
          <a className={styles.secondaryButton} href={`https://store.steampowered.com/app/${type.workAppid}/`} target="_blank" rel="noopener noreferrer">
            {ko ? '원작 게임 보기' : 'See the original game'}<span className={styles.srOnly}>{ko ? ' (새 창)' : ' (opens a new tab)'}</span>
          </a>
        </div>
        <div role="status" className={styles.shareStatus}>
          {share === 'copied' ? (ko ? '링크를 복사했어요.' : 'Link copied.') : null}
          {share === 'manual' ? <label>{ko ? '아래 주소를 선택해서 복사해주세요.' : 'Select and copy the address below.'}
            <input readOnly value={shareUrl} onFocus={event => event.target.select()} aria-label={ko ? '공유 주소' : 'Share URL'} /></label> : null}
        </div>
      </div>
    </article>
    <section className={styles.nextStep}>
      <div><h2>{ko ? '캐릭터는 닮은꼴, 추천은 내 취향대로.' : 'A familiar face. Your own taste.'}</h2>
        <p>{ko ? '이 페이지는 유형 소개예요. 게임을 골라보며 실제 취향도 찾아보세요.' : 'This is a type profile. Discover your own taste by choosing games.'}</p></div>
      <Link href={`/${locale}/steam/choice`} className={styles.secondaryButton}>{ko ? '게임 선택 테스트' : 'Try the game-pick test'}</Link>
    </section>
    <nav className={styles.pager} aria-label={ko ? '다른 캐릭터' : 'Other characters'}>
      <Link href={`/${locale}/taste/${previous.code}`}><span>{ko ? '이전 캐릭터' : 'Previous'}</span><strong>{previous.name[locale]}</strong></Link>
      <Link href={`/${locale}/taste`}>{ko ? '16개 유형 모두 보기' : 'View all 16 types'}</Link>
      <Link href={`/${locale}/taste/${next.code}`}><span>{ko ? '다음 캐릭터' : 'Next'}</span><strong>{next.name[locale]}</strong></Link>
    </nav>
    <footer className={styles.footer}>
      <p>{ko ? '게임 캐릭터에 빗댄 비공식 플레이 취향입니다. 원작의 공식 설정이나 실제 테스트 결과가 아닙니다.' : 'An unofficial play-style analogy, not canonical character lore or a personal test result.'}</p>
      <p>{ko ? '캐릭터와 작품의 권리는 각 권리자에게 있습니다. Playalike는 독립 비영리 프로젝트입니다.' : 'Characters and games belong to their respective owners. Playalike is an independent, non-commercial project.'}</p>
    </footer>
  </main>;
}
