import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import TasteQuiz from '@/components/taste/TasteQuiz';
import { isLocale } from '@/lib/i18n/locales';

type Props = { params: Promise<{ locale: string }> };
export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  return { title: locale === 'ko' ? '나의 게이머 유형 찾기 | Playalike' : 'Find your player type | Playalike', robots: { index: false, follow: true } };
}
export default async function TasteTestPage({ params }: Props) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  return <TasteQuiz key={locale} locale={locale} />;
}
