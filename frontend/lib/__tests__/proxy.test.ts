import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import { config, proxy } from "../../proxy";

function req(path: string, headers: Record<string, string> = {}) {
  return new NextRequest(new URL(path, "https://steam-game-recommend.vercel.app"), { headers });
}

describe("proxy", () => {
  it("언어 없는 주소를 브라우저 언어 주소로 307 이동하고 쿼리를 유지한다", () => {
    const res = proxy(req("/steam/input?q=a", { "accept-language": "ko-KR" }));
    expect(res.status).toBe(307);
    expect(res.headers.get("location")).toMatch(/\/ko\/steam\/input\?q=a$/);
  });

  it("쿠키가 브라우저 언어보다 먼저다", () => {
    const res = proxy(req("/steam/input?q=a", { "accept-language": "ko-KR", cookie: "locale=en" }));
    expect(res.status).toBe(307);
    expect(res.headers.get("location")).toMatch(/\/en\/steam\/input\?q=a$/);
  });

  it.each(["/metrics", "/en/steam"])("%s 는 그대로 통과시킨다", (path) => {
    expect(proxy(req(path)).headers.get("location")).toBeNull();
  });
});

describe("proxy matcher", () => {
  // 이 패턴은 Next 문자열 그대로 JS 정규식으로 읽어도 같은 뜻이다
  const re = new RegExp("^" + config.matcher[0] + "$");

  it.each(["/robots.txt", "/sitemap.xml", "/favicon.ico", "/googleaaacd619e1a42923.html", "/_next/static/x.js"])(
    "%s 는 건너뛴다",
    (path) => {
      expect(re.test(path)).toBe(false);
    }
  );

  it.each(["/", "/steam", "/steam/input", "/en/steam"])("%s 는 거친다", (path) => {
    expect(re.test(path)).toBe(true);
  });
});
