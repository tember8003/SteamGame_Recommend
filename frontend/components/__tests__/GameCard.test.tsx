import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import GameCard from "../GameCard";
import type { SteamApp } from "@/lib/types";

// sgrFeedback 은 실제 fetch 를 태우므로 모듈째 목킹해 호출 인자만 검증함
vi.mock("@/lib/feedback", () => ({
  sgrFeedback: vi.fn(),
}));

import { sgrFeedback } from "@/lib/feedback";

// vitest 는 RTL 자동 cleanup 을 붙이지 않으므로 매 테스트 뒤 DOM 을 직접 비움
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const BASE_GAME: SteamApp = {
  appid: 400,
  name: "Portal",
  shortDescription: "중력을 거스르는 퍼즐 게임임",
  headerImage: "https://example.com/portal.jpg",
};

describe("GameCard", () => {
  it("이름과 이미지를 렌더함", () => {
    render(<GameCard game={BASE_GAME} />);

    expect(screen.getByText("Portal")).toBeInTheDocument();
    const image = screen.getByRole("img", { name: "Portal" });
    expect(image).toHaveAttribute("src", BASE_GAME.headerImage);
  });

  it("steamStore 가 있으면 새 탭 스토어 링크를 렌더함", () => {
    const game: SteamApp = { ...BASE_GAME, steamStore: "https://store.steampowered.com/app/400" };
    render(<GameCard game={game} />);

    const link = screen.getByRole("link", { name: /view on steam/i });
    expect(link).toHaveAttribute("href", game.steamStore);
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("steamStore 가 없으면 링크를 렌더하지 않음", () => {
    render(<GameCard game={BASE_GAME} />);

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("feedback prop 이 없으면 좋아요,싫어요 버튼을 렌더하지 않음", () => {
    render(<GameCard game={BASE_GAME} />);

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("feedback prop 이 있으면 좋아요,싫어요 버튼을 렌더함", () => {
    render(<GameCard game={BASE_GAME} feedback={{ context: "INPUT", rank: 0 }} />);

    expect(screen.getByRole("button", { name: "Like this game" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Dislike this game" })).toBeInTheDocument();
  });

  it("좋아요 클릭 시 sgrFeedback 을 UP 신호로 호출함", () => {
    render(
      <GameCard
        game={BASE_GAME}
        feedback={{ context: "RANDOM", rank: 2, steamId: "76561198000000000" }}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: "Like this game" }));

    expect(sgrFeedback).toHaveBeenCalledWith(
      BASE_GAME.appid,
      "UP",
      "RANDOM",
      2,
      "76561198000000000"
    );
  });

  it("싫어요 클릭 시 sgrFeedback 을 DOWN 신호로 호출함", () => {
    render(<GameCard game={BASE_GAME} feedback={{ context: "SIMILAR", rank: 1 }} />);

    fireEvent.click(screen.getByRole("button", { name: "Dislike this game" }));

    expect(sgrFeedback).toHaveBeenCalledWith(BASE_GAME.appid, "DOWN", "SIMILAR", 1, undefined);
  });

  it("클릭 즉시(응답을 기다리지 않고) 눌린 버튼을 aria-pressed 로 표시하고, 반대 버튼을 누르면 전환함", () => {
    render(<GameCard game={BASE_GAME} feedback={{ context: "INPUT", rank: 0 }} />);
    const likeBtn = screen.getByRole("button", { name: "Like this game" });
    const dislikeBtn = screen.getByRole("button", { name: "Dislike this game" });

    expect(likeBtn).toHaveAttribute("aria-pressed", "false");
    expect(dislikeBtn).toHaveAttribute("aria-pressed", "false");

    fireEvent.click(likeBtn);
    expect(likeBtn).toHaveAttribute("aria-pressed", "true");
    expect(dislikeBtn).toHaveAttribute("aria-pressed", "false");

    fireEvent.click(dislikeBtn);
    expect(likeBtn).toHaveAttribute("aria-pressed", "false");
    expect(dislikeBtn).toHaveAttribute("aria-pressed", "true");
  });

  it("스토어 링크 클릭 시 CLICK 신호를 보내고 이동은 막지 않음", () => {
    const game: SteamApp = { ...BASE_GAME, steamStore: "https://store.steampowered.com/app/400" };
    render(<GameCard game={game} feedback={{ context: "INPUT", rank: 0 }} />);

    const link = screen.getByRole("link", { name: /view on steam/i });
    // fireEvent.click 의 반환값은 핸들러가 preventDefault 를 호출하지 않으면 true 임(이동이 막히지 않았다는 뜻)
    const notPrevented = fireEvent.click(link);

    expect(sgrFeedback).toHaveBeenCalledWith(game.appid, "CLICK", "INPUT", 0, undefined);
    expect(notPrevented).toBe(true);
  });
});
