import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import type { Core } from "cytoscape";

let core: Core;
vi.mock("cytoscape", async (load) => {
  const { default: cytoscape } = await load<{ default: typeof import("cytoscape") }>();
  return { default: () => (core = cytoscape({ headless: true })) };
});
vi.mock("@/lib/api", () => ({
  graphSearch: vi.fn(async () => [{ appid: 620, name: "Portal 2", image: "" }]),
  graphNeighbors: vi.fn(async () => ({ neighbors: [] })),
}));
import SimilarityGraph from "../SimilarityGraph";

afterEach(() => { cleanup(); vi.clearAllMocks(); });

describe("graph controls", () => {
  it("supports labelled search, button zoom and expanded view without a wheel", async () => {
    render(<SimilarityGraph />);
    fireEvent.change(screen.getByRole("textbox", { name: "Search and select a game" }), { target: { value: "Portal" } });
    fireEvent.click(await screen.findByRole("button", { name: "Portal 2" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Zoom in" })).toBeEnabled());
    const before = core.zoom();
    fireEvent.click(screen.getByRole("button", { name: "Zoom in" }));
    expect(core.zoom()).toBeGreaterThan(before);
    fireEvent.click(screen.getByRole("button", { name: "Expand view" }));
    expect(screen.getByRole("button", { name: "Close expanded view" })).toHaveAttribute("aria-pressed", "true");
    fireEvent.keyDown(screen.getByRole("region", { name: "Similarity Graph" }), { key: "Escape" });
    expect(screen.getByRole("button", { name: "Expand view" })).toHaveAttribute("aria-pressed", "false");
  });
  it("offers a keyboard alternative to canvas-only nodes", async () => {
    render(<SimilarityGraph />);
    fireEvent.change(screen.getByRole("textbox", { name: "Search and select a game" }), { target: { value: "Portal" } });
    fireEvent.click(await screen.findByRole("button", { name: "Portal 2" }));
    expect(screen.getByRole("combobox", { name: "Games on this graph" })).toHaveTextContent("Portal 2");
    expect(screen.getByRole("button", { name: "Explore selected game" })).toBeEnabled();
  });
});
