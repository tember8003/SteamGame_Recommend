import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";

vi.mock("@/lib/api", () => ({ recommendMatch: vi.fn() }));
const mockGet = vi.fn();
vi.mock("next/navigation", () => ({ useSearchParams: () => ({ get: mockGet }) }));

import { recommendMatch } from "@/lib/api";
import MatchPage from "./page";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("MatchPage", () => {
  it("rawText 와 구조화 컨트롤이 모두 비면 제출하지 않는다", () => {
    mockGet.mockReturnValue(null);
    render(<MatchPage />);
    fireEvent.click(screen.getByRole("button", { name: /match/i }));
    expect(recommendMatch).not.toHaveBeenCalled();
  });

  it("rawText 가 있으면 recommendMatch 를 부르고 결과를 렌더한다", async () => {
    mockGet.mockReturnValue(null);
    vi.mocked(recommendMatch).mockResolvedValue({
      criteria: { nlParseFailed: false },
      games: [{ appid: 1, name: "Alpha", headerImage: "a.jpg" }],
      reviews: [{ positiveRating: 90 }],
      explanations: [{ reasons: [{ code: "IS_FREE", params: {} }], caveats: [] }],
      shortfall: false,
    });

    render(<MatchPage />);
    fireEvent.change(screen.getByLabelText(/describe/i), { target: { value: "cozy co-op" } });
    fireEvent.click(screen.getByRole("button", { name: /match/i }));

    await waitFor(() => expect(screen.getByText("Alpha")).toBeInTheDocument());
    expect(recommendMatch).toHaveBeenCalledTimes(1);
  });

  it("nlParseFailed 면 안내 문구를 함께 보여준다", async () => {
    mockGet.mockReturnValue(null);
    vi.mocked(recommendMatch).mockResolvedValue({
      criteria: { nlParseFailed: true },
      games: [{ appid: 1, name: "Alpha", headerImage: "a.jpg" }],
      reviews: [{}],
      explanations: [{ reasons: [], caveats: [] }],
      shortfall: false,
    });

    render(<MatchPage />);
    fireEvent.change(screen.getByLabelText(/describe/i), { target: { value: "???" } });
    fireEvent.click(screen.getByRole("button", { name: /match/i }));

    await waitFor(() => expect(screen.getByText(/could not fully read/i)).toBeInTheDocument());
  });

  it("결과가 0건이면 no matches 안내를 보여준다", async () => {
    mockGet.mockReturnValue(null);
    vi.mocked(recommendMatch).mockResolvedValue({
      criteria: { nlParseFailed: false },
      games: [],
      reviews: [],
      explanations: [],
      shortfall: false,
    });

    render(<MatchPage />);
    fireEvent.change(screen.getByLabelText(/describe/i), { target: { value: "cozy" } });
    fireEvent.click(screen.getByRole("button", { name: /match/i }));

    await waitFor(() => expect(screen.getByText(/no matches/i)).toBeInTheDocument());
  });

  it("429 면 rateLimited 안내를 보여준다", async () => {
    mockGet.mockReturnValue(null);
    vi.mocked(recommendMatch).mockRejectedValue(
      new Error("POST /api/recommend/match failed: 429")
    );

    render(<MatchPage />);
    fireEvent.change(screen.getByLabelText(/describe/i), { target: { value: "cozy" } });
    fireEvent.click(screen.getByRole("button", { name: /match/i }));

    await waitFor(() => expect(screen.getByText(/too many requests/i)).toBeInTheDocument());
  });

  it("그 밖의 오류는 error 안내를 보여준다", async () => {
    mockGet.mockReturnValue(null);
    vi.mocked(recommendMatch).mockRejectedValue(new Error("boom"));

    render(<MatchPage />);
    fireEvent.change(screen.getByLabelText(/describe/i), { target: { value: "cozy" } });
    fireEvent.click(screen.getByRole("button", { name: /match/i }));

    await waitFor(() => expect(screen.getByText(/an error occurred/i)).toBeInTheDocument());
  });

  it("다시추천은 이미 나온 appid 를 excludeAppids 로 넘긴다", async () => {
    mockGet.mockReturnValue(null);
    vi.mocked(recommendMatch).mockResolvedValue({
      criteria: { nlParseFailed: false },
      games: [{ appid: 1, name: "Alpha", headerImage: "a.jpg" }],
      reviews: [{ positiveRating: 90 }],
      explanations: [{ reasons: [{ code: "IS_FREE", params: {} }], caveats: [] }],
      shortfall: false,
    });

    render(<MatchPage />);
    fireEvent.change(screen.getByLabelText(/describe/i), { target: { value: "cozy" } });
    fireEvent.click(screen.getByRole("button", { name: /match/i }));
    await waitFor(() => expect(screen.getByText("Alpha")).toBeInTheDocument());

    // 첫 검색은 제외 없이 나감
    expect(vi.mocked(recommendMatch).mock.calls[0][0].excludeAppids).toBeUndefined();

    fireEvent.click(screen.getByRole("button", { name: /recommend again/i }));
    await waitFor(() => expect(recommendMatch).toHaveBeenCalledTimes(2));

    // 다시추천은 방금 본 appid 1 을 제외 집합으로 실어 보냄
    expect(vi.mocked(recommendMatch).mock.calls[1][0].excludeAppids).toEqual([1]);
  });
});
