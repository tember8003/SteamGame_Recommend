"use client";

import Button from "./ui/Button";
import LanguageToggle from "./LanguageToggle";
import { useT } from "@/lib/i18n/useT";

// 헤더 우측 액션 묶음, 언어 토글 + 비활성 로그인,회원가입 자리표시자
export default function HeaderActions() {
  const t = useT();
  return (
    <>
      <LanguageToggle />
      <Button variant="glass" disabled>{t("header.login")}</Button>
      <Button variant="violet" disabled>{t("header.signup")}</Button>
    </>
  );
}
