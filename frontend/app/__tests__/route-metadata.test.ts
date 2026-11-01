/// <reference types="vite/client" />
import { existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it, vi } from "vitest";
import { INDEX_ROUTES, SITE_URL } from "@/lib/seo/routes";
import LocaleLayout, { generateMetadata as homeMetadata, generateStaticParams } from "../[locale]/layout";

vi.mock("../fonts", () => ({ gmarketSans: { variable: "" } }));
vi.mock("@/components/CosmicBackground", () => ({ default: () => null }));

type Layout = {
  generateMetadata: (a: { params: Promise<{ locale: string }> }) => Promise<{ alternates?: { canonical?: unknown } }>;
};

// 대괄호가 글롭 문자 집합으로 읽히지 않게 넓게 모은 뒤 키로 거른다
const layouts = import.meta.glob<Layout>("../**/layout.tsx", { eager: true });

function layoutFor(path: string): Layout | undefined {
  const suffix = `[locale]${path}/layout.tsx`;
  return Object.entries(layouts).find(([key]) => key.endsWith(suffix))?.[1];
}

const screens = Object.entries(INDEX_ROUTES).filter(([key]) => key !== "home");

describe("화면별 메타데이터 연결", () => {
  it.each(screens)("%s (%s) 레이아웃이 자기 주소를 canonical 로 내보낸다", async (_key, path) => {
    const layout = layoutFor(path);
    expect(layout, `${path} 레이아웃이 없다`).toBeDefined();
    const meta = await layout!.generateMetadata({ params: Promise.resolve({ locale: "ko" }) });
    expect(meta.alternates?.canonical).toBe(`/ko${path}`);
  });

  it.each(screens)("%s (%s) 화면 page.tsx 가 있다", (_key, path) => {
    const file = join(dirname(fileURLToPath(import.meta.url)), "..", "[locale]", ...path.split("/"), "page.tsx");
    expect(existsSync(file)).toBe(true);
  });
});

describe("언어 레이아웃", () => {
  it("홈 메타데이터는 canonical /ko 와 metadataBase 를 가진다", async () => {
    const meta = await homeMetadata({ params: Promise.resolve({ locale: "ko" }) });
    expect(meta.alternates?.canonical).toBe("/ko");
    expect(meta.metadataBase).toEqual(new URL(SITE_URL));
  });

  it("빌드 때 만들 언어는 en, ko 두 개다", () => {
    expect(generateStaticParams()).toEqual([{ locale: "en" }, { locale: "ko" }]);
  });

  it("지원하지 않는 언어는 404 로 보낸다", async () => {
    await expect(LocaleLayout({ children: null, params: Promise.resolve({ locale: "fr" }) })).rejects.toMatchObject({
      digest: expect.stringContaining("404"),
    });
  });
});
