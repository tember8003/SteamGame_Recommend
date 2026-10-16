"use client";

import { usePathname, useRouter } from "next/navigation";
import SegmentedControl from "./ui/SegmentedControl";
import { useLocale } from "@/lib/i18n/LocaleProvider";
import { isLocale, LOCALE_COOKIE } from "@/lib/i18n/locales";
import { switchLocalePath } from "@/lib/i18n/routing";
import { useT } from "@/lib/i18n/useT";

// 언어 이름은 그 언어의 글자로 보여주므로 번역하지 않는다
const OPTIONS = [
  { value: "en", label: "EN" },
  { value: "ko", label: "한" },
];

const ONE_YEAR_SECONDS = 60 * 60 * 24 * 365;

// 헤더 언어 토글, 같은 화면의 다른 언어 주소로 옮기고 고른 언어를 쿠키에 남긴다
export default function LanguageToggle() {
  const locale = useLocale();
  const pathname = usePathname();
  const router = useRouter();
  const t = useT();

  function handleChange(value: string) {
    if (!isLocale(value) || value === locale) return;
    document.cookie = `${LOCALE_COOKIE}=${value}; path=/; max-age=${ONE_YEAR_SECONDS}; samesite=lax`;
    // 쿼리는 클릭 시점의 주소에서 읽는다, useSearchParams 를 쓰면 헤더가 있는 정적 화면 전체가 클라이언트 렌더링으로 밀린다
    router.push(`${switchLocalePath(pathname ?? "/", value)}${window.location.search}`);
  }

  return (
    <SegmentedControl
      options={OPTIONS}
      value={locale}
      onChange={handleChange}
      ariaLabel={t("header.language")}
    />
  );
}
