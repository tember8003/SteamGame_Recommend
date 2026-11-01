import { apiUrl } from '../api';
import type { Locale } from '../i18n/locales';
import type { TypeView } from './api';

// 공유 메타데이터는 정적 생성 때 읽고 캐시한다. API 장애가 화면 전체의 빌드를 막지 않게 한다.
export async function publicTasteMetadata(code: string, locale: Locale): Promise<TypeView | null> {
  try {
    const response = await fetch(apiUrl(`/api/taste/types/${encodeURIComponent(code)}?lang=${locale}`), {
      next: { revalidate: 3600 }, signal: AbortSignal.timeout(6000),
    });
    if (!response.ok) return null;
    const type: TypeView = await response.json();
    return type.code === code && typeof type.share?.title === 'string' && typeof type.share?.text === 'string' ? type : null;
  } catch { return null }
}
