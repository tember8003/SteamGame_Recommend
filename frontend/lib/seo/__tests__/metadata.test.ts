import { describe, expect, it } from "vitest";
import { en } from "@/lib/i18n/messages/en";
import { ko } from "@/lib/i18n/messages/ko";
import { buildMetadata, metadataFor } from "../metadata";
import { INDEX_ROUTES, type RouteKey } from "../routes";

const ROUTE_KEYS = Object.keys(INDEX_ROUTES) as RouteKey[];

describe("buildMetadata", () => {
  it("화면 이름 뒤에 서비스 이름을 붙이고 canonical 과 세 개의 hreflang 을 준다", () => {
    const m = buildMetadata("en", "input");
    expect(m.title).toBe(`${en.meta.input.title} | Playalike`);
    expect(m.description).toBe(en.meta.input.description);
    expect(m.alternates?.canonical).toBe("/en/steam/input");
    expect(m.alternates?.languages).toEqual({
      ko: "/ko/steam/input",
      en: "/en/steam/input",
      "x-default": "/steam/input",
    });
  });

  it("홈은 제목을 그대로 쓰고 x-default 는 / 다", () => {
    const m = buildMetadata("ko", "home");
    expect(m.title).toBe(ko.meta.home.title);
    expect(m.alternates?.canonical).toBe("/ko");
    expect(m.alternates?.languages).toEqual({ ko: "/ko", en: "/en", "x-default": "/" });
  });

  it("Open Graph 에 제목, 설명, 주소, 이름, 언어를 넣는다", () => {
    const m = buildMetadata("ko", "match");
    expect(m.openGraph).toMatchObject({
      title: `${ko.meta.match.title} | Playalike`,
      description: ko.meta.match.description,
      url: "/ko/steam/match",
      siteName: "Playalike",
      locale: "ko_KR",
      type: "website",
    });
    expect(buildMetadata("en", "match").openGraph).toMatchObject({ locale: "en_US" });
  });

  it("Open Graph 이미지를 언어별 주소로 명시한다", () => {
    expect(buildMetadata("ko", "match").openGraph).toMatchObject({
      images: [{ url: "/ko/opengraph-image", width: 1200, height: 630, alt: "Playalike" }],
    });
    expect(buildMetadata("en", "home").openGraph).toMatchObject({
      images: [{ url: "/en/opengraph-image", width: 1200, height: 630, alt: "Playalike" }],
    });
  });

  it.each(["en", "ko"] as const)("%s 화면끼리 제목과 설명이 겹치지 않는다", (locale) => {
    const all = ROUTE_KEYS.map((route) => buildMetadata(locale, route));
    expect(new Set(all.map((m) => m.title)).size).toBe(ROUTE_KEYS.length);
    expect(new Set(all.map((m) => m.description)).size).toBe(ROUTE_KEYS.length);
  });

  it("한국어 제목과 설명에는 한글이 있고 영어 쪽에는 없다", () => {
    for (const route of ROUTE_KEYS) {
      expect(ko.meta[route].title).toMatch(/[가-힣]/);
      expect(ko.meta[route].description).toMatch(/[가-힣]/);
      expect(en.meta[route].title).not.toMatch(/[가-힣]/);
      expect(en.meta[route].description).not.toMatch(/[가-힣]/);
    }
  });
});

describe("metadataFor", () => {
  it("params 의 언어로 메타데이터를 만든다", async () => {
    const m = await metadataFor("similar")({ params: Promise.resolve({ locale: "ko" }) });
    expect(m.alternates?.canonical).toBe("/ko/steam/similar");
  });

  it("언어가 잘못되면 en 으로 만든다", async () => {
    const m = await metadataFor("similar")({ params: Promise.resolve({ locale: "fr" }) });
    expect(m.alternates?.canonical).toBe("/en/steam/similar");
  });
});
