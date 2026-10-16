import { describe, expect, it } from "vitest";
import { firstQueryValue } from "../searchParams";

describe("firstQueryValue", () => {
  it("문자열은 그대로, 배열은 첫 값, 없으면 빈 문자열", () => {
    expect(firstQueryValue("cozy")).toBe("cozy");
    expect(firstQueryValue(["a", "b"])).toBe("a");
    expect(firstQueryValue([])).toBe("");
    expect(firstQueryValue(undefined)).toBe("");
  });
});
