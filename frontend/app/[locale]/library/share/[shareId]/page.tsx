import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import LibraryShared from '@/components/library/LibraryShared';
import { isLocale } from '@/lib/i18n/locales';
import { libraryCopy } from '@/lib/library/copy';
import { SITE_URL } from '@/lib/seo/routes';

export async function generateMetadata({ params }: { params: Promise<{ locale: string; shareId: string }> }): Promise<Metadata> {
  const { locale, shareId } = await params;
  const c = libraryCopy[isLocale(locale) ? locale : 'en'];
  const url = `${SITE_URL}/${isLocale(locale) ? locale : 'en'}/library/share/${encodeURIComponent(shareId)}`;
  return { title: `${c.shared} | Playalike`, description: c.shareInfo, robots: { index: false, follow: false }, alternates: { canonical: url, languages: {} }, openGraph: { title: `${c.shared} | Playalike`, description: c.shareInfo, url } };
}
export default async function SharedLibraryPage({ params }: { params: Promise<{ locale: string; shareId: string }> }) {
  const { locale, shareId } = await params;
  if (!isLocale(locale)) notFound();
  return <LibraryShared key={`${locale}-${shareId}`} locale={locale} shareId={shareId} />;
}
