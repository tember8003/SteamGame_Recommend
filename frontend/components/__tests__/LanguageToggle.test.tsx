import { afterEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { LocaleProvider } from "@/lib/i18n/LocaleProvider";
import LanguageToggle from "../LanguageToggle";

afterEach(() => {
  cleanup();
  window.localStorage.clear();
});

describe("LanguageToggle", () => {
  it("EN,한 세그먼트를 렌더하고 기본은 EN 이 선택된다", () => {
    render(
      <LocaleProvider>
        <LanguageToggle />
      </LocaleProvider>
    );
    expect(screen.getByRole("radio", { name: "EN" })).toHaveAttribute("aria-checked", "true");
  });

  it("한 을 누르면 로케일 선택을 sgr_locale 에 저장한다", () => {
    render(
      <LocaleProvider>
        <LanguageToggle />
      </LocaleProvider>
    );
    fireEvent.click(screen.getByRole("radio", { name: "한" }));
    expect(window.localStorage.getItem("sgr_locale")).toBe("ko");
    expect(screen.getByRole("radio", { name: "한" })).toHaveAttribute("aria-checked", "true");
  });
});
