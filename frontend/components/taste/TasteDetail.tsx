"use client";

import Link from 'next/link';
import { useState } from 'react';
import type { Locale } from '@/lib/i18n/locales';
import { tasteAxes, tasteTypes, type TasteType } from '@/lib/taste/catalog';
import TastePortrait from './TastePortrait';
import styles from './taste.module.css';
import { recordTasteEvent, type TasteResult, type TypeView } from '@/lib/taste/api';
import TasteGames from './TasteGames';

export default function TasteDetail({ type, locale, apiType, result }: { type: TasteType; locale: Locale; apiType?: TypeView; result?: TasteResult }) {
  const ko = locale === 'ko';
  const [share, setShare] = useState<'idle' | 'copied' | 'manual'>('idle');
  const [shareUrl, setShareUrl] = useState('');
  const index = tasteTypes.findIndex(item => item.code === type.code);
  const previous = tasteTypes[(index + 15) % 16];
  const next = tasteTypes[(index + 1) % 16];
  async function copyLink() {
    const url = `${window.location.origin}/${locale}/taste/${type.code}?s=1`;
    setShareUrl(url);
    recordTasteEvent('SHARE_CLICK', type.code, 'LINK');
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
        <p className={styles.detailLabel}>{result ? (ko ? '당신의 게이머 유형' : 'Your player type') : (ko ? '이런 플레이어라면' : 'For the player who…')}</p>
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
    {result ? <section className={styles.scores} aria-label={ko ? '내 답변으로 본 성향' : 'Your answers by preference'}>
      <h2>{ko ? '내 답변으로 본 성향' : 'Your answers by preference'}</h2>
      {tasteAxes.map(axis => {
        const values = result.axisScores[axis.map(side => side.code).join('')];
        return <div key={axis[0].code} className={styles.scoreRow}>{axis.map(side => <label key={side.code}>
          <span>{side[locale]} <strong>{values?.[side.code] ?? 0} / 3</strong></span>
          <meter min={0} max={3} value={values?.[side.code] ?? 0} aria-label={side[locale]} />
        </label>)}</div>;
      })}
    </section> : null}
    {apiType ? <TasteGames key={`${locale}-${type.code}`} view={apiType} result={result} locale={locale} /> : null}
    <section className={styles.nextStep}>
      <div><h2>{ko ? '캐릭터는 닮은꼴, 추천은 내 취향대로.' : 'A familiar face. Your own taste.'}</h2>
        <p>{ko ? '12가지 질문으로 내 플레이 취향을 알아보세요.' : 'Discover your play style with twelve questions.'}</p></div>
      <Link href={`/${locale}/taste/test`} className={styles.secondaryButton}>{result ? (ko ? '다시 테스트하기' : 'Take the test again') : (ko ? '내 유형 찾기' : 'Find my type')}</Link>
    </section>
    <nav className={styles.pager} aria-label={ko ? '다른 캐릭터' : 'Other characters'}>
      <Link href={`/${locale}/taste/${previous.code}`}><span>{ko ? '이전 캐릭터' : 'Previous'}</span><strong>{previous.name[locale]}</strong></Link>
      <Link href={`/${locale}/taste`}>{ko ? '16개 유형 모두 보기' : 'View all 16 types'}</Link>
      <Link href={`/${locale}/taste/${next.code}`}><span>{ko ? '다음 캐릭터' : 'Next'}</span><strong>{next.name[locale]}</strong></Link>
    </nav>
    <footer className={styles.footer}>
      <p>{result ? (ko ? '게임 캐릭터에 빗댄 비공식 플레이 취향입니다. 결과는 방금 제출한 답변을 바탕으로 합니다.' : 'An unofficial character analogy based on the answers you just submitted.') : (ko ? '게임 캐릭터에 빗댄 비공식 플레이 취향입니다. 원작의 공식 설정이나 실제 테스트 결과가 아닙니다.' : 'An unofficial play-style analogy, not canonical character lore or a personal test result.')}</p>
      <p>{ko ? '캐릭터와 작품의 권리는 각 권리자에게 있습니다. Playalike는 독립 비영리 프로젝트입니다.' : 'Characters and games belong to their respective owners. Playalike is an independent, non-commercial project.'}</p>
    </footer>
  </main>;
}
