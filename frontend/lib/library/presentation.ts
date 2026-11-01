import { ApiError } from '../api';
import type { Locale } from '../i18n/locales';
import type { LibraryText } from './api';
import { libraryCopy } from './copy';

export function fillLibraryText(value: LibraryText): string {
  return value.text.replace(/\{(\w+)\}/g, (placeholder, key: string) => String(value.params[key] ?? placeholder));
}
export function libraryError(error: unknown, locale: Locale): string {
  const c = libraryCopy[locale];
  if (!(error instanceof ApiError)) return c.error;
  const codes: Record<string, string> = { INVALID_STEAM_ID: c.invalid, STEAM_PROFILE_NOT_FOUND: c.notFound, LIBRARY_PRIVATE: c.private, LIBRARY_EMPTY: c.emptyLibrary, SHARE_NOT_FOUND: c.expired };
  return codes[error.code] ?? (error.status === 429 ? c.rate : error.status === 503 ? c.unavailable : error.status === 400 ? c.invalid : c.error);
}
export const numberText = (value: number, locale: Locale) => new Intl.NumberFormat(locale).format(value);
export const hoursText = (value: number, locale: Locale) => new Intl.NumberFormat(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value);
export const percentText = (ratio: number, locale: Locale) => new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 1 }).format(ratio);
