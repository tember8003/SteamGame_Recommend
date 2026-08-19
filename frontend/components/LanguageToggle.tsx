"use client";

import SegmentedControl from "./ui/SegmentedControl";
import { useLocale } from "@/lib/i18n/LocaleProvider";
import { useT } from "@/lib/i18n/useT";

// 언어 이름은 그 언어의 글자로 보여주므로 번역하지 않는다
const OPTIONS = [
  { value: "en", label: "EN" },
  { value: "ko", label: "한" },
];

// 헤더 언어 토글, 라우트는 바꾸지 않고 로케일만 바꾼다
export default function LanguageToggle() {
  const { locale, setLocale } = useLocale();
  const t = useT();
  return (
    <SegmentedControl
      options={OPTIONS}
      value={locale}
      onChange={(v) => setLocale(v as "en" | "ko")}
      ariaLabel={t("header.language")}
    />
  );
}
