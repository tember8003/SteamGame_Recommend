import { afterEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { LocaleProvider } from "@/lib/i18n/LocaleProvider";
import SteamHubPage from "../page";

afterEach(() => cleanup());

describe("SteamHubPage", () => {
  it("세 장소의 안내판에서 기존 아홉 기능을 모두 고를 수 있다", () => {
    render(<LocaleProvider locale="ko"><SteamHubPage /></LocaleProvider>);
    for (const name of ["취향 점술소", "게임 탐색소", "플레이 기록관"]) {
      expect(screen.getByRole("region", { name })).toBeVisible();
    }
    expect(screen.getByRole("main").querySelectorAll("a")).toHaveLength(9);
  });
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
