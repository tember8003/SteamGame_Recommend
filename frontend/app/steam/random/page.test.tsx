import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";

vi.mock("@/lib/api", () => ({
  recommendRandom: vi.fn(),
}));

import { recommendRandom } from "@/lib/api";
import RandomRecommendPage from "./page";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("RandomRecommendPage 결과 없음 처리", () => {
  it("404 는 오류가 아니라 맞는 게임 없음 안내로 보여준다", async () => {
    vi.mocked(recommendRandom).mockRejectedValue(new Error("POST /api/recommend/random failed: 404"));

    render(<RandomRecommendPage />);
    fireEvent.click(screen.getByRole("button", { name: "Get Recommendations" }));

    expect(await screen.findByText("No games found")).toBeInTheDocument();
    expect(screen.getByText("Try different tag combinations!")).toBeInTheDocument();
    expect(screen.queryByText("An error occurred")).not.toBeInTheDocument();
  });

  it("그 밖의 실패는 그대로 오류로 보여준다", async () => {
    vi.mocked(recommendRandom).mockRejectedValue(new Error("POST /api/recommend/random failed: 500"));

    render(<RandomRecommendPage />);
    fireEvent.click(screen.getByRole("button", { name: "Get Recommendations" }));

    expect(await screen.findByText("An error occurred")).toBeInTheDocument();
  });
});
