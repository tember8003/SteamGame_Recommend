import { afterEach, describe, expect, it, vi } from "vitest";
import { usePathname } from "next/navigation";
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { LocaleProvider } from "@/lib/i18n/LocaleProvider";
import Header from "../Header";

afterEach(() => { cleanup(); vi.mocked(usePathname).mockReturnValue("/en"); });

describe("Header", () => {
  it("AI 의뢰 화면에서도 게임 탐색소의 현재 위치를 표시한다", () => {
    vi.mocked(usePathname).mockReturnValue("/ko/steam/input");
    render(<LocaleProvider locale="ko"><Header /></LocaleProvider>);
    expect(screen.getByRole("link", { name: "게임 탐색소" })).toHaveAttribute("aria-current", "location");
  });
  it.each(["ko", "en"] as const)("%s 기능 화면에서 마을로 이동하고 현재 위치를 표시한다", (locale) => {
    vi.mocked(usePathname).mockReturnValue(`/${locale}/steam/profile`);
    render(<LocaleProvider locale={locale}><Header /></LocaleProvider>);
    expect(screen.getByRole("link", { name: locale === "ko" ? "마을로 돌아가기" : "Back to the village" })).toHaveAttribute("href", `/${locale}`);
    expect(screen.getByRole("link", { name: locale === "ko" ? "플레이 기록관" : "The archivist" })).toHaveAttribute("aria-current", "location");
  });
  it("로고 링크가 현재 언어의 홈으로 간다", () => {
    render(
      <LocaleProvider locale="ko">
        <Header />
      </LocaleProvider>
    );
    expect(screen.getByRole("link", { name: "Playalike home" })).toHaveAttribute("href", "/ko");
  });
});
