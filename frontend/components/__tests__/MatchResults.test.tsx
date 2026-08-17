import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import MatchResults from "../MatchResults";
import type { MatchExplanation, ReviewResponse, SteamApp } from "@/lib/types";

vi.mock("@/lib/feedback", () => ({ sgrFeedback: vi.fn() }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const GAMES: SteamApp[] = [
  { appid: 1, name: "Alpha", headerImage: "a.jpg" },
  { appid: 2, name: "Beta", headerImage: "b.jpg" },
];
const REVIEWS: ReviewResponse[] = [{ positiveRating: 90 }, { positiveRating: 80 }];
const EXPLANATIONS: MatchExplanation[] = [
  { reasons: [{ code: "IS_FREE", params: {} }], caveats: [] },
  { reasons: [{ code: "VOLUME_FITS", params: {} }], caveats: [] },
];
const OPTS = { showHeader: true, showHours: false, showRatingBar: true, showReviewText: false };

describe("MatchResults", () => {
  it("게임마다 카드와 설명을 렌더하고 피드백 버튼을 붙인다", () => {
    render(
      <MatchResults
        games={GAMES}
        reviews={REVIEWS}
        explanations={EXPLANATIONS}
        shortfall={false}
        reviewOptions={OPTS}
        onRecommendAgain={() => {}}
      />
    );
    expect(screen.getByText("Alpha")).toBeInTheDocument();
    expect(screen.getByText("Free to play")).toBeInTheDocument();
    expect(screen.getByText("Fits your session length")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Like this game" })).toHaveLength(2);
  });

  it("shortfall 이 true 면 안내 배너를 보여준다", () => {
    render(
      <MatchResults
        games={GAMES}
        reviews={REVIEWS}
        explanations={EXPLANATIONS}
        shortfall={true}
        reviewOptions={OPTS}
        onRecommendAgain={() => {}}
      />
    );
    expect(screen.getByText(/could not fill every pick/i)).toBeInTheDocument();
  });

  it("다시추천 버튼이 콜백을 부른다", () => {
    const onAgain = vi.fn();
    render(
      <MatchResults
        games={GAMES}
        reviews={REVIEWS}
        explanations={EXPLANATIONS}
        shortfall={false}
        reviewOptions={OPTS}
        onRecommendAgain={onAgain}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: /recommend again/i }));
    expect(onAgain).toHaveBeenCalledTimes(1);
  });
});
