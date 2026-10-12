import { describe, expect, it } from "vitest";
import { makeT } from "../useT";
import { en } from "../messages/en";
import { ko } from "../messages/ko";

describe("makeT", () => {
  it("점경로로 리프 문구를 찾는다", () => {
    const t = makeT(en);
    expect(t("header.tagline")).toBe(en.header.tagline);
  });

  it("한국어 카탈로그는 한국어 문구를 낸다", () => {
    const t = makeT(ko);
    expect(t("header.tagline")).toBe(ko.header.tagline);
  });

  it("아예 없는 키는 키 문자열을 그대로 돌려준다", () => {
    const t = makeT(en);
    expect(t("nope.nada")).toBe("nope.nada");
  });
});
