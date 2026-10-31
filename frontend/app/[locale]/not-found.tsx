"use client";

import Header from "@/components/Header";
import WorkshopIntro from "@/components/WorkshopIntro";
import Button from "@/components/ui/Button";
import { useLocale } from "@/lib/i18n/LocaleProvider";
import { localeHref } from "@/lib/i18n/routing";
import { useT } from "@/lib/i18n/useT";
import styles from "./steam/page.module.css";

// 언어 주소 아래에서 맞는 화면이 없을 때 보이는 404, 허브 화면의 제목 스타일을 그대로 쓴다
export default function NotFound() {
  const t = useT();
  const locale = useLocale();
  return (
    <div className={styles.page}>
      <Header />
      <main className={styles.main}>
        <WorkshopIntro place="village" title={t("notFound.title")} subtitle={t("notFound.desc")} />
        <Button variant="glass" href={localeHref(locale, "/")}>
          {t("notFound.home")}
        </Button>
      </main>
    </div>
  );
}
