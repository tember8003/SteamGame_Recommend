import { afterEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { LocaleProvider } from "@/lib/i18n/LocaleProvider";
import { ko } from "@/lib/i18n/messages/ko";
import Home from "../page";

afterEach(() => cleanup());

describe("Home", () => {
  it("스팀 카드 링크에 언어를 붙인다", () => {
    render(
      <LocaleProvider locale="ko">
        <Home />
      </LocaleProvider>
    );
    expect(screen.getByRole("link", { name: ko.landing.steamCta })).toHaveAttribute("href", "/ko/steam");
  });
});
