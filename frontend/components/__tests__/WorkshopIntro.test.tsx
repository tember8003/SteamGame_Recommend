import { afterEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { LocaleProvider } from "@/lib/i18n/LocaleProvider";
import WorkshopIntro from "../WorkshopIntro";

afterEach(cleanup);

describe("WorkshopIntro", () => {
  it.each(["ko", "en"] as const)("%s 화면에 기능 제목과 번역된 장소를 표시한다", (locale) => {
    render(<LocaleProvider locale={locale}><WorkshopIntro place="archive" title="Profile" subtitle="Public profiles only" /></LocaleProvider>);
    expect(screen.getByRole("heading", { level: 1, name: "Profile" })).toBeVisible();
    expect(screen.getByText(locale === "ko" ? "플레이 기록관" : "The archivist")).toBeVisible();
    expect(screen.getByText("Public profiles only")).toBeVisible();
  });
});
