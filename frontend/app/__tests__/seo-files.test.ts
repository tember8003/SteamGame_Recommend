import { describe, expect, it, vi } from "vitest";
import robots from "../robots";
import sitemap from "../sitemap";
import { metadata as internalMetadata } from "../(internal)/layout";

vi.mock("../fonts", () => ({ gmarketSans: { variable: "" } }));

const SITE = "https://playalike.online";

describe("robots", () => {
  it("모든 수집 프로그램에 전체를 열고 sitemap 주소를 알린다", () => {
    const r = robots();
    expect(r.rules).toEqual([{ userAgent: "*", allow: "/" }]);
    expect(r.sitemap).toBe(`${SITE}/sitemap.xml`);
  });
});

describe("sitemap", () => {
  const entries = sitemap();

  it("12개 화면 x 2개 언어 = 24개 주소", () => {
    expect(entries).toHaveLength(24);
    expect(new Set(entries.map((e) => e.url)).size).toBe(24);
  });

  it("모든 주소에 언어 접두어가 있고 /metrics 는 없다", () => {
    for (const e of entries) {
      expect(e.url).toMatch(new RegExp(`^${SITE}/(en|ko)(/|$)`));
      expect(e.url).not.toContain("/metrics");
    }
  });

  it("홈과 화면의 언어별 짝을 alternates 로 넣는다", () => {
    const koInput = entries.find((e) => e.url === `${SITE}/ko/steam/input`);
    expect(koInput?.alternates?.languages).toEqual({ ko: `${SITE}/ko/steam/input`, en: `${SITE}/en/steam/input` });
    const enHome = entries.find((e) => e.url === `${SITE}/en`);
    expect(enHome?.alternates?.languages).toEqual({ ko: `${SITE}/ko`, en: `${SITE}/en` });
  });

  it("수정 시각을 넣지 않는다", () => {
    expect(entries.every((e) => e.lastModified === undefined)).toBe(true);
  });
});

describe("/metrics 레이아웃", () => {
  it("검색엔진이 색인하지 않게 한다", () => {
    expect(internalMetadata.robots).toEqual({ index: false, follow: false });
  });
});
