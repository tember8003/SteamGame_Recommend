import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";

// recommendRecent 호출 인자, sgrFeedback 전달 인자만 검증하면 되므로 두 모듈 모두 목킹함
vi.mock("@/lib/api", () => ({
  recommendRecent: vi.fn(),
}));
vi.mock("@/lib/feedback", () => ({
  sgrFeedback: vi.fn(),
}));

import { recommendRecent } from "@/lib/api";
import { sgrFeedback } from "@/lib/feedback";
import type { RecommendationResult } from "@/lib/types";
import RecentRecommendPage from "../page";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const RESULT: RecommendationResult = {
  usedTags: ["Indie"],
  recommendedGame: [{ appid: 620, name: "Portal 2", headerImage: "https://example.com/portal2.jpg" }],
  review: [{ positiveRating: 95, reviewSummary: "Overwhelmingly Positive" }],
};

describe("RecentRecommendPage", () => {
  it("입력한 SteamID(앞뒤 공백 제거)로 recommendRecent 를 호출하고, 결과 카드의 피드백은 context=RECENT, steamId 를 함께 보낸다", async () => {
    vi.mocked(recommendRecent).mockResolvedValue(RESULT);

    render(<RecentRecommendPage />);

    fireEvent.change(screen.getByRole("textbox", { name: "Steam ID" }), {
      target: { value: "  76561198000000001  " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Get Recommendations" }));

    await waitFor(() =>
      expect(recommendRecent).toHaveBeenCalledWith(
        "76561198000000001",
        expect.objectContaining({ review: 1000, koreanCheck: false, freeCheck: false })
      )
    );

    await screen.findByText("Portal 2");

    fireEvent.click(screen.getByRole("button", { name: "Dislike this game" }));

    expect(sgrFeedback).toHaveBeenCalledWith(620, "DOWN", "RECENT", 0, "76561198000000001");
  });

  it("SteamID 를 비운 채 제출하면 recommendRecent 를 호출하지 않는다", () => {
    render(<RecentRecommendPage />);

    fireEvent.click(screen.getByRole("button", { name: "Get Recommendations" }));

    expect(recommendRecent).not.toHaveBeenCalled();
  });
});
