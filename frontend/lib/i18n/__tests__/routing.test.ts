import { describe, expect, it } from "vitest";
import { isLocale } from "../locales";
import { localeHref, localeOfPath, localeRedirectPath, pickLocale, switchLocalePath } from "../routing";

describe("isLocale", () => {
  it("en, ko 만 언어로 본다", () => {
    expect(isLocale("en")).toBe(true);
    expect(isLocale("ko")).toBe(true);
    expect(isLocale("ja")).toBe(false);
    expect(isLocale(undefined)).toBe(false);
  });
});

describe("pickLocale", () => {
  it("쿠키가 올바른 값이면 브라우저 언어보다 먼저 쓴다", () => {
    expect(pickLocale("en", "ko-KR,ko;q=0.9")).toBe("en");
    expect(pickLocale("ko", "en-US")).toBe("ko");
  });

  it("쿠키 값이 잘못되면 무시하고 브라우저 언어를 본다", () => {
    expect(pickLocale("fr", "ko-KR")).toBe("ko");
  });

  it("품질값 순서로 처음 나오는 지원 언어를 고른다", () => {
    expect(pickLocale(undefined, "ko-KR,ko;q=0.9,en-US;q=0.8")).toBe("ko");
    expect(pickLocale(undefined, "en-US,en;q=0.9,ko;q=0.8")).toBe("en");
    expect(pickLocale(undefined, "ja,ko;q=0.9")).toBe("ko");
    expect(pickLocale(undefined, "en;q=0.5,ko;q=0.8")).toBe("ko");
  });

  it("품질값 0 은 받지 않는다는 뜻이라 건너뛴다", () => {
    expect(pickLocale(undefined, "ko;q=0,en;q=0.1")).toBe("en");
    expect(pickLocale(undefined, "ko;q=0")).toBe("en");
  });

  it("헤더가 없거나 지원 언어가 없으면 en", () => {
    expect(pickLocale(undefined, null)).toBe("en");
    expect(pickLocale(undefined, "")).toBe("en");
    expect(pickLocale(undefined, "fr-FR,de;q=0.8")).toBe("en");
  });
});

describe("localeOfPath", () => {
  it("첫 경로 조각이 언어일 때만 돌려준다", () => {
    expect(localeOfPath("/ko")).toBe("ko");
    expect(localeOfPath("/en/steam/input")).toBe("en");
    expect(localeOfPath("/english")).toBeUndefined();
    expect(localeOfPath("/steam")).toBeUndefined();
    expect(localeOfPath("/")).toBeUndefined();
  });
});

describe("localeRedirectPath", () => {
  it("언어 없는 주소에 언어를 붙이고 쿼리를 유지한다", () => {
    expect(localeRedirectPath("/", "", "ko")).toBe("/ko");
    expect(localeRedirectPath("/steam/input", "?q=cozy", "ko")).toBe("/ko/steam/input?q=cozy");
    expect(localeRedirectPath("/nope", "", "en")).toBe("/en/nope");
  });

  it("언어가 이미 있으면 옮기지 않는다", () => {
    expect(localeRedirectPath("/ko", "", "en")).toBeNull();
    expect(localeRedirectPath("/en/steam", "?q=1", "ko")).toBeNull();
  });

  it("/metrics 와 그 아래는 옮기지 않는다", () => {
    expect(localeRedirectPath("/metrics", "", "ko")).toBeNull();
    expect(localeRedirectPath("/metrics/x", "", "ko")).toBeNull();
  });

  it("/metrics 로 시작하는 다른 주소는 옮긴다", () => {
    expect(localeRedirectPath("/metricsfoo", "", "en")).toBe("/en/metricsfoo");
  });
});

describe("localeHref", () => {
  it("홈은 언어 주소 자체, 나머지는 앞에 언어를 붙인다", () => {
    expect(localeHref("ko", "/")).toBe("/ko");
    expect(localeHref("en", "/steam/match")).toBe("/en/steam/match");
  });
});

describe("switchLocalePath", () => {
  it("첫 조각의 언어만 바꾼다", () => {
    expect(switchLocalePath("/en/steam/input", "ko")).toBe("/ko/steam/input");
    expect(switchLocalePath("/ko", "en")).toBe("/en");
    expect(switchLocalePath("/en/", "ko")).toBe("/ko");
  });

  it("언어가 없는 주소면 앞에 붙인다", () => {
    expect(switchLocalePath("/steam", "ko")).toBe("/ko/steam");
    expect(switchLocalePath("/", "en")).toBe("/en");
  });
});
