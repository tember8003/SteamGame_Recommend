import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";

// recommendProfile 호출 인자, sgrFeedback 전달 인자만 검증하면 되므로 두 모듈 모두 목킹함
vi.mock("@/lib/api", () => ({
  recommendProfile: vi.fn(),
}));
vi.mock("@/lib/feedback", () => ({
  sgrFeedback: vi.fn(),
}));

import { recommendProfile } from "@/lib/api";
import { sgrFeedback } from "@/lib/feedback";
import type { RecommendationResult } from "@/lib/types";
import ProfileRecommendPage from "../page";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const RESULT: RecommendationResult = {
  usedTags: ["RPG"],
  recommendedGame: [{ appid: 400, name: "Portal", headerImage: "https://example.com/portal.jpg" }],
  review: [{ positiveRating: 90, reviewSummary: "Very Positive" }],
};

describe("ProfileRecommendPage", () => {
  it("accepts a numeric profile URL and explains invalid names without a request", async () => {
    vi.mocked(recommendProfile).mockResolvedValue(RESULT);
    render(<ProfileRecommendPage />);
    const input = screen.getByRole("textbox", { name: "Steam ID" });
    fireEvent.change(input, { target: { value: "my_display_name" } });
    fireEvent.click(screen.getByRole("button", { name: "Get Recommendations" }));
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByRole("alert")).toHaveTextContent("17-digit");
    expect(recommendProfile).not.toHaveBeenCalled();
    expect(screen.getByText("Where do I find my Steam ID?")).toBeInTheDocument();
    fireEvent.change(input, { target: { value: "https://steamcommunity.com/profiles/76561198000000000/" } });
    fireEvent.click(screen.getByRole("button", { name: "Get Recommendations" }));
    await waitFor(() => expect(recommendProfile).toHaveBeenCalledWith("76561198000000000", expect.anything()));
    expect(input).toHaveAttribute("aria-invalid", "false");
  });
  it("입력한 SteamID(앞뒤 공백 제거)로 recommendProfile 을 호출하고, 결과 카드의 피드백은 context=PROFILE, steamId 를 함께 보낸다", async () => {
    vi.mocked(recommendProfile).mockResolvedValue(RESULT);

    render(<ProfileRecommendPage />);

    fireEvent.change(screen.getByRole("textbox", { name: "Steam ID" }), {
      target: { value: "  76561198000000000  " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Get Recommendations" }));

    await waitFor(() =>
      expect(recommendProfile).toHaveBeenCalledWith(
        "76561198000000000",
        expect.objectContaining({ review: 1000, koreanCheck: false, freeCheck: false })
      )
    );

    await screen.findByText("Portal");

    fireEvent.click(screen.getByRole("button", { name: "Like this game" }));

    expect(sgrFeedback).toHaveBeenCalledWith(400, "UP", "PROFILE", 0, "76561198000000000");
  });

  it("SteamID 를 비운 채 제출하면 recommendProfile 을 호출하지 않는다", () => {
    render(<ProfileRecommendPage />);

    fireEvent.click(screen.getByRole("button", { name: "Get Recommendations" }));

    expect(recommendProfile).not.toHaveBeenCalled();
  });
});
