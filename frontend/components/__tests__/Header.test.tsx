import { afterEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { LocaleProvider } from "@/lib/i18n/LocaleProvider";
import Header from "../Header";

afterEach(() => cleanup());

describe("Header", () => {
  it("로고 링크가 현재 언어의 홈으로 간다", () => {
    render(
      <LocaleProvider locale="ko">
        <Header />
      </LocaleProvider>
    );
    expect(screen.getByRole("link", { name: "Playalike home" })).toHaveAttribute("href", "/ko");
  });
});
