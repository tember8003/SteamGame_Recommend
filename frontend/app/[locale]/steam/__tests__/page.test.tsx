import { afterEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { LocaleProvider } from "@/lib/i18n/LocaleProvider";
import SteamHubPage from "../page";

afterEach(() => cleanup());

describe("SteamHubPage", () => {
  it("match 페이지로 가는 카드를 렌더한다", () => {
    render(
      <LocaleProvider locale="en">
        <SteamHubPage />
      </LocaleProvider>
    );
    const link = screen.getByRole("link", { name: /requirement match/i });
    expect(link).toHaveAttribute("href", "/en/steam/match");
  });

  it("한국어 화면의 카드는 /ko 주소로 간다", () => {
    render(
      <LocaleProvider locale="ko">
        <SteamHubPage />
      </LocaleProvider>
    );
    expect(screen.getByRole("link", { name: /요구사항 매칭/ })).toHaveAttribute("href", "/ko/steam/match");
  });
});
