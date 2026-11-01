import data from './catalog.json';
import type { Locale } from '@/lib/i18n/locales';

export type TasteType = (typeof data)[number];
export const tasteTypes: readonly TasteType[] = data;

export const tasteAxes = [
  [{ code: 'E', ko: '탐험', en: 'Exploration' }, { code: 'G', ko: '성장', en: 'Growth' }],
  [{ code: 'N', ko: '서사', en: 'Story' }, { code: 'M', ko: '시스템', en: 'Systems' }],
  [{ code: 'H', ko: '도전', en: 'Challenge' }, { code: 'C', ko: '여유', en: 'Leisure' }],
  [{ code: 'S', ko: '솔로', en: 'Solo' }, { code: 'P', ko: '파티', en: 'Party' }],
] as const;

export function getTasteType(code: string): TasteType | undefined {
  return tasteTypes.find(type => type.code === code.toUpperCase());
}

export function filterTasteTypes(query: string, side: string, locale: Locale, types: readonly TasteType[] = tasteTypes): TasteType[] {
  const needle = query.trim().toLocaleLowerCase();
  return types.filter(type => (!side || type.code.includes(side)) &&
    [type.code, type.name[locale], type.work[locale], type.oneLiner[locale], type.traits[locale]]
      .some(value => value.toLocaleLowerCase().includes(needle)));
}
