import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { usePathname, useRouter } from "next/navigation";
import { LocaleProvider } from "@/lib/i18n/LocaleProvider";
import LanguageToggle from "../LanguageToggle";

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(usePathname).mockReturnValue("/en");
  window.history.pushState({}, "", "/");
});

afterEach(() => {
  cleanup();
  document.cookie = "locale=; path=/; max-age=0";
});

describe("LanguageToggle", () => {
  it("레이아웃이 넘긴 언어가 선택되어 있다", () => {
    render(
      <LocaleProvider locale="ko">
        <LanguageToggle />
      </LocaleProvider>
    );
    expect(screen.getByRole("radio", { name: "한" })).toHaveAttribute("aria-checked", "true");
  });

  it("다른 언어를 누르면 같은 화면의 그 언어 주소로 옮기고 쿼리를 유지한다", () => {
    vi.mocked(usePathname).mockReturnValue("/en/steam/input");
    window.history.pushState({}, "", "/en/steam/input?q=cozy");
    render(
      <LocaleProvider locale="en">
        <LanguageToggle />
      </LocaleProvider>
    );
    fireEvent.click(screen.getByRole("radio", { name: "한" }));
    expect(useRouter().push).toHaveBeenCalledWith("/ko/steam/input?q=cozy");
  });

  it("고른 언어를 쿠키에 남긴다", () => {
    vi.mocked(usePathname).mockReturnValue("/en");
    render(
      <LocaleProvider locale="en">
        <LanguageToggle />
      </LocaleProvider>
    );
    fireEvent.click(screen.getByRole("radio", { name: "한" }));
    expect(document.cookie).toContain("locale=ko");
  });

  it("지금 언어를 다시 누르면 아무 일도 하지 않는다", () => {
    render(
      <LocaleProvider locale="en">
        <LanguageToggle />
      </LocaleProvider>
    );
    fireEvent.click(screen.getByRole("radio", { name: "EN" }));
    expect(useRouter().push).not.toHaveBeenCalled();
  });
});
