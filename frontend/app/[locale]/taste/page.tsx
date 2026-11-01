import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import TasteAtlas from '@/components/taste/TasteAtlas';
import { isLocale } from '@/lib/i18n/locales';
import { SITE_URL } from '@/lib/seo/routes';

type Props = { params: Promise<{ locale: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  const ko = locale === 'ko';
  const title = ko ? '16가지 게이머 유형 도감 | Playalike' : '16 player characters | Playalike';
  const description = ko ? '익숙한 게임 캐릭터로 만나는 16가지 플레이 취향. 나와 닮은 캐릭터를 찾아보세요.' : 'Explore sixteen play styles through familiar game characters.';
  const url = `${SITE_URL}/${locale}/taste`;
  const image = `${SITE_URL}/images/taste/GMHS.png`;
  return {
    title, description,
    alternates: { canonical: `${SITE_URL}/${locale}/taste`, languages: { ko: `${SITE_URL}/ko/taste`, en: `${SITE_URL}/en/taste` } },
    openGraph: { title, description, url, type: 'website', images: [{ url: image, width: 1254, height: 1254, alt: title }] },
    twitter: { card: 'summary', title, description, images: [image] },
  };
}

export default async function TastePage({ params }: Props) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  return <TasteAtlas locale={locale} />;
}
