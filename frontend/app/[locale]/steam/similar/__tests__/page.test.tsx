import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";

// gamesSearch 호출 여부,횟수만 확인하면 되므로 lib/api 전체를 목킹함(이 페이지는 gamesSearch, recommendSimilar 만 씀)
vi.mock("@/lib/api", () => ({
  gamesSearch: vi.fn(),
  recommendSimilar: vi.fn(),
}));

import { gamesSearch } from "@/lib/api";
import SimilarRecommendPage from "../page";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.useRealTimers();
});

describe("SimilarRecommendPage 자동완성 경합", () => {
  it("디바운스 대기 중 바깥을 클릭해 닫으면, 대기 중이던 타이머가 나중에 발화해도 gamesSearch 를 다시 쏘지 않는다", async () => {
    vi.useFakeTimers();
    vi.mocked(gamesSearch).mockResolvedValue([{ gameName: "Portal 2", headerImage: "img.jpg" }]);

    render(<SimilarRecommendPage />);
    const input = screen.getByPlaceholderText("Enter game name for recommendations");

    fireEvent.change(input, { target: { value: "portal" } });

    // 디바운스(280ms) 가 끝나기 전에 입력창 바깥을 클릭해 자동완성을 닫음
    fireEvent.mouseDown(document.body);

    // 디바운스 시간을 넘겨도 이미 취소됐어야 할 타이머라 gamesSearch 가 호출되면 안 됨
    await vi.advanceTimersByTimeAsync(400);

    expect(gamesSearch).not.toHaveBeenCalled();
  });

  it("디바운스가 끝나 조회가 나간 뒤 바깥을 클릭해 닫으면, 늦게 오는 응답이 목록을 다시 열지 않는다", async () => {
    vi.useFakeTimers();
    let resolveSearch: (items: { gameName: string; headerImage: string }[]) => void = () => {};
    vi.mocked(gamesSearch).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveSearch = resolve;
        })
    );

    render(<SimilarRecommendPage />);
    const input = screen.getByPlaceholderText("Enter game name for recommendations");

    fireEvent.change(input, { target: { value: "portal" } });

    // 디바운스를 끝내 실제 gamesSearch 호출까지 발생시킴(응답은 아직 도착 전, pending 상태)
    await vi.advanceTimersByTimeAsync(400);
    expect(gamesSearch).toHaveBeenCalledTimes(1);

    // 응답이 오기 전에 바깥을 클릭해 자동완성을 닫음
    fireEvent.mouseDown(document.body);

    // 그 다음에야 늦은 응답이 도착함
    resolveSearch([{ gameName: "Portal 2", headerImage: "img.jpg" }]);
    await Promise.resolve();
    await Promise.resolve();

    expect(screen.queryByText("Portal 2")).not.toBeInTheDocument();
  });
});
