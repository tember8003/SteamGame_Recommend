import type { Locale } from '../i18n/locales';
import { baseGenres, genreName } from './base';
import { numberText } from './presentation';

export const picksCopy = {
  ko: {
    playNext: '창고에서 꺼내볼 게임', nextHint: '이미 가지고 있는 게임에서, 익숙한 취향과 만나는 첫 모험을 골랐어요.',
    revisit: '다시 이어갈 모험', revisitHint: '오래 즐겼던 세계가 다음 이야기를 기다리고 있어요.',
    abandoned: '잠시 멀어진 장르', abandonedHint: '짧게 머문 기록이에요. 싫어하는 장르라고 단정할 수는 없어요.',
    otherGenre: '기타 장르', examples: '짧게 머문 게임', history: '가장 오래 머문 세 세계',
  },
  en: {
    playNext: 'Take a game off the shelf', nextHint: 'A first adventure in a game you already own, chosen for your familiar tastes.',
    revisit: 'Adventures worth returning to', revisitHint: 'Worlds you spent time in are waiting for another chapter.',
    abandoned: 'A genre you stepped away from', abandonedHint: 'These are brief visits, not proof that you dislike a genre.',
    otherGenre: 'Other genres', examples: 'Games you briefly visited', history: 'Your three longest stays',
  },
};
export const matchedReason = (tags: string[], locale: Locale) => tags.length
  ? locale === 'ko' ? `많이 즐긴 ${tags.join(', ')} 태그가 있는 게임` : `Shares tags you play often: ${tags.join(', ')}`
  : locale === 'ko' ? '내 플레이 기록에서 고른 게임' : 'Chosen from your play history';
export const idleReason = (days: number, locale: Locale) => locale === 'ko'
  ? `분석 당시 마지막 플레이 ${numberText(days, locale)}일 전`
  : `Last played ${numberText(days, locale)} days before analysis`;
export const pickedGenreName = (genre: string, locale: Locale) => Object.hasOwn(baseGenres, genre)
  ? genreName(genre, locale) : picksCopy[locale].otherGenre;
export const briefVisits = (count: number, locale: Locale) => locale === 'ko'
  ? `${numberText(count, locale)}개 게임에 짧게 머물렀어요.`
  : `Brief visits to ${numberText(count, locale)} ${count === 1 ? 'game' : 'games'}.`;
