import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { renderToString } from "react-dom/server";
import { LocaleProvider } from "@/lib/i18n/LocaleProvider";
import Home from "../page";
vi.mock('@/lib/taste/api', () => ({ recordLandingVisit: vi.fn(), recordTasteEvent: vi.fn() }));

afterEach(() => cleanup());

describe("Home", () => {
  it.each(["ko", "en"] as const)("%s 홈의 세 장소와 보조 링크에 언어를 유지한다", (locale) => {
    render(<LocaleProvider locale={locale}><Home /></LocaleProvider>);
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    const main = screen.getByRole("main");
    for (const path of ["/taste/test", "/steam/choice", "/library", "/steam", "/steam/review-guess", "/steam/random"]) {
      expect(main.querySelector(`a[href="/${locale}${path}"]`)).toBeInTheDocument();
    }
    expect(screen.getByRole("heading", { name: locale === "ko" ? "게임 취향 테스트" : "Game taste test" })).toBeVisible();
    expect(screen.getByText(locale === "ko" ? "16가지 상황으로 내 게이머 유형 찾기" : "Find your player type in 16 questions")).toBeVisible();
  });

  it("탐색소를 펼치면 세 가지 실제 추천 기능을 선택할 수 있다", () => {
    render(<LocaleProvider locale="ko"><Home /></LocaleProvider>);
    const summary = screen.getByText("게임 찾기");
    const details = summary.closest("details")!;
    expect(details).not.toHaveAttribute("open");
    fireEvent.click(summary);
    expect(details).toHaveAttribute("open");
    expect(within(details).getByRole("link", { name: "게임명으로 찾기" })).toHaveAttribute("href", "/ko/steam/similar");
    expect(within(details).getByRole("link", { name: "AI에게 설명하기" })).toHaveAttribute("href", "/ko/steam/input");
    expect(within(details).getByRole("link", { name: "태그로 찾기" })).toHaveAttribute("href", "/ko/steam/random");
    fireEvent.click(summary);
    expect(details).not.toHaveAttribute("open");
  });

  it("자바스크립트 실행 전 HTML에도 주요 문구와 기존 기능 링크가 있다", () => {
    const html = renderToString(<LocaleProvider locale="en"><Home /></LocaleProvider>);
    expect(html).not.toContain("What will you play tonight?");
    expect(html).not.toContain("Discover your taste. Find a new world to get lost in.");
    expect(html).toContain('href="/en/steam/choice"');
    expect(html).toContain('href="/en/steam/similar"');
    expect(html).toContain('href="/en/library"');
  });
});
