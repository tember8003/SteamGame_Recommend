import { afterEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { LocaleProvider } from "@/lib/i18n/LocaleProvider";
import HeaderTagline from "../HeaderTagline";
import LanguageToggle from "../LanguageToggle";

afterEach(() => {
  cleanup();
  window.localStorage.clear();
});

describe("HeaderTagline", () => {
  it("언어 토글을 따라 부제 문구가 바뀐다", () => {
    render(
      <LocaleProvider>
        <LanguageToggle />
        <HeaderTagline />
      </LocaleProvider>
    );
    expect(screen.getByText("Steam game recommendations")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("radio", { name: "한" }));

    expect(screen.getByText("Steam 게임 추천")).toBeInTheDocument();
  });
});
