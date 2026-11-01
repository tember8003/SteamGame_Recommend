import { notFound } from 'next/navigation';
import LibraryWorkbench from '@/components/library/LibraryWorkbench';
import { isLocale } from '@/lib/i18n/locales';

export default async function LibraryPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  return <LibraryWorkbench key={locale} locale={locale} />;
}
