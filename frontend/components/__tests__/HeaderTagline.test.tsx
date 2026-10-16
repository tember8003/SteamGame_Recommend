import { afterEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { LocaleProvider } from "@/lib/i18n/LocaleProvider";
import HeaderTagline from "../HeaderTagline";

afterEach(() => cleanup());

describe("HeaderTagline", () => {
  it("레이아웃이 넘긴 언어로 부제를 그린다", () => {
    render(
      <LocaleProvider locale="ko">
        <HeaderTagline />
      </LocaleProvider>
    );
    expect(screen.getByText("Steam 게임 추천")).toBeInTheDocument();
  });

  it("프로바이더 밖에서는 영어로 그린다", () => {
    render(<HeaderTagline />);
    expect(screen.getByText("Steam game recommendations")).toBeInTheDocument();
  });
});
