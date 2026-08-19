import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import SegmentedControl from "../SegmentedControl";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const OPTIONS = [
  { value: "en", label: "EN" },
  { value: "ko", label: "한" },
];

describe("SegmentedControl", () => {
  it("옵션을 라디오로 렌더하고 현재 값을 aria-checked 로 표시한다", () => {
    render(<SegmentedControl options={OPTIONS} value="en" onChange={() => {}} ariaLabel="Language" />);
    const group = screen.getByRole("radiogroup", { name: "Language" });
    expect(group).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "EN" })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("radio", { name: "한" })).toHaveAttribute("aria-checked", "false");
  });

  it("클릭하면 그 값으로 onChange 를 부른다", () => {
    const onChange = vi.fn();
    render(<SegmentedControl options={OPTIONS} value="en" onChange={onChange} ariaLabel="Language" />);
    fireEvent.click(screen.getByRole("radio", { name: "한" }));
    expect(onChange).toHaveBeenCalledWith("ko");
  });

  it("오른쪽 화살표로 이웃 값을 고른다", () => {
    const onChange = vi.fn();
    render(<SegmentedControl options={OPTIONS} value="en" onChange={onChange} ariaLabel="Language" />);
    fireEvent.keyDown(screen.getByRole("radio", { name: "EN" }), { key: "ArrowRight" });
    expect(onChange).toHaveBeenCalledWith("ko");
  });
});
