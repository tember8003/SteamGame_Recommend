import { afterEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import ReviewPanel from "../ReviewPanel";
import { en } from "@/lib/i18n/messages/en";

afterEach(() => {
  cleanup();
});

const OPTS = { showHeader: false, showRatingBar: false, showReviewText: false };

describe("ReviewPanel", () => {
  it("긍정 비율과 종합 평가 두 통계만 보여 준다", () => {
    const { container } = render(
      <ReviewPanel review={{ positiveRating: 91, reviewSummary: "Very Positive" }} options={OPTS} />
    );

    expect(screen.getByText("91%")).toBeInTheDocument();
    expect(screen.getByText(en.review.positiveRating)).toBeInTheDocument();
    expect(screen.getByText("Very Positive")).toBeInTheDocument();
    expect(screen.getByText(en.review.overallRating)).toBeInTheDocument();
    // 플레이 시간 통계는 데이터가 비어 있어 더는 그리지 않는다
    expect(screen.queryByText("Pos Hours")).not.toBeInTheDocument();
    expect(container.textContent).not.toMatch(/\dh\b/);
  });
});
