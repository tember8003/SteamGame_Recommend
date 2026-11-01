import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import TasteDetail from '@/components/taste/TasteDetail';
import { getTasteType, tasteTypes } from '@/lib/taste/catalog';
import { isLocale } from '@/lib/i18n/locales';
import { SITE_URL } from '@/lib/seo/routes';

type Props = { params: Promise<{ locale: string; code: string }> };

export function generateStaticParams() {
  return tasteTypes.map(({ code }) => ({ code }));
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, code } = await params;
  const type = getTasteType(code);
  if (!isLocale(locale) || !type) return { title: 'Playalike', robots: { index: false } };
  const title = `${type.name[locale]} / ${type.code} | Playalike`;
  const description = type.oneLiner[locale];
  const url = `${SITE_URL}/${locale}/taste/${type.code}`;
  return {
    title, description,
    alternates: { canonical: url, languages: { ko: `${SITE_URL}/ko/taste/${type.code}`, en: `${SITE_URL}/en/taste/${type.code}` } },
    openGraph: { title, description, url, type: 'website', images: [{ url: `${SITE_URL}/images/taste/${type.code}.png`, width: 1254, height: 1254, alt: type.name[locale] }] },
    twitter: { card: 'summary', title, description, images: [`${SITE_URL}/images/taste/${type.code}.png`] },
  };
}

export default async function TasteTypePage({ params }: Props) {
  const { locale, code } = await params;
  const type = getTasteType(code);
  if (!isLocale(locale) || !type) notFound();
  return <TasteDetail key={type.code} type={type} locale={locale} />;
}
