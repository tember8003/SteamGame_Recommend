import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { renderToString } from "react-dom/server";

// cytoscape 본체는 브라우저 전용이라 테스트에서는 빈 컴포넌트로 바꾼다
vi.mock("next/dynamic", () => ({ default: () => () => null }));

import { en } from "@/lib/i18n/messages/en";
import { LocaleProvider } from "@/lib/i18n/LocaleProvider";
import { ko } from "@/lib/i18n/messages/ko";
import GraphPage from "./page";

afterEach(() => cleanup());

describe("GraphPage", () => {
  it("그래프 본체를 불러오기 전에도 제목과 설명이 있다", () => {
    render(<GraphPage />);
    expect(screen.getByRole("heading", { level: 1, name: en.graph.title })).toBeInTheDocument();
    expect(screen.getByText(en.graph.subtitle)).toBeInTheDocument();
  });

  it("서버 HTML 에 제목과 한국어 설명이 들어 있다", () => {
    const html = renderToString(
      <LocaleProvider locale="ko">
        <GraphPage />
      </LocaleProvider>
    );
    expect(html).toContain("<h1");
    expect(html).toContain(ko.graph.subtitle);
  });
});
