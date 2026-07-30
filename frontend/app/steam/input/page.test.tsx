import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";

// recommendInput 이 자동 호출되지 않아야 함을 확인하기 위해 lib/api 를 목킹함
vi.mock("@/lib/api", () => ({
  recommendInput: vi.fn(),
}));

// useSearchParams 가 반환할 값을 테스트마다 바꿔야 하므로 next/navigation 을 목킹함
const mockGet = vi.fn();
vi.mock("next/navigation", () => ({
  useSearchParams: () => ({ get: mockGet }),
}));

import { recommendInput } from "@/lib/api";
import InputRecommendPage from "./page";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("InputRecommendPage ?q= 프리필", () => {
  it("?q= 값이 있으면 입력창 초기값으로 채우고, recommendInput 은 호출하지 않는다", () => {
    mockGet.mockImplementation((key: string) => (key === "q" ? "cozy" : null));

    render(<InputRecommendPage />);

    const textarea = screen.getByLabelText("Game Features") as HTMLTextAreaElement;
    expect(textarea.value).toBe("cozy");
    expect(recommendInput).not.toHaveBeenCalled();
  });

  it("?q= 가 없으면 입력창이 빈 문자열로 시작한다", () => {
    mockGet.mockReturnValue(null);

    render(<InputRecommendPage />);

    const textarea = screen.getByLabelText("Game Features") as HTMLTextAreaElement;
    expect(textarea.value).toBe("");
  });
});
