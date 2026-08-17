import { afterEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import SteamHubPage from "../page";

afterEach(() => cleanup());

describe("SteamHubPage", () => {
  it("match 페이지로 가는 카드를 렌더한다", () => {
    render(<SteamHubPage />);
    const link = screen.getByRole("link", { name: /requirement match/i });
    expect(link).toHaveAttribute("href", "/steam/match");
  });
});
