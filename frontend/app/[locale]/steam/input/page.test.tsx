import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";

// recommendInput 이 자동 호출되지 않아야 함을 확인하기 위해 lib/api 를 목킹함
vi.mock("@/lib/api", () => ({
  recommendInput: vi.fn(),
}));

import { recommendInput } from "@/lib/api";
import InputPage from "./page";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

async function renderPage(query: Record<string, string | string[] | undefined>) {
  render(await InputPage({ searchParams: Promise.resolve(query) }));
  return screen.getByLabelText("Game Features") as HTMLTextAreaElement;
}

describe("InputPage ?q= 프리필", () => {
  it("?q= 값이 있으면 입력창 초기값으로 채우고, recommendInput 은 호출하지 않는다", async () => {
    expect((await renderPage({ q: "cozy" })).value).toBe("cozy");
    expect(recommendInput).not.toHaveBeenCalled();
  });

  it("?q= 가 없으면 입력창이 빈 문자열로 시작한다", async () => {
    expect((await renderPage({})).value).toBe("");
  });

  it("?q= 가 두 번 오면 첫 값을 쓴다", async () => {
    expect((await renderPage({ q: ["a", "b"] })).value).toBe("a");
  });

  it("서버에서 그릴 제목이 있다", async () => {
    await renderPage({});
    expect(screen.getByRole("heading", { level: 1 })).toBeInTheDocument();
  });
});
