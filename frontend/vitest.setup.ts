import { vi } from "vitest";

// 화면 테스트는 App Router 밖에서 그려서 라우터 훅을 대신한다, 테스트에서 값을 바꾸려면 vi.mocked 를 쓴다
const router = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  prefetch: vi.fn(),
  back: vi.fn(),
  forward: vi.fn(),
  refresh: vi.fn(),
}));

vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  useRouter: vi.fn(() => router),
  usePathname: vi.fn(() => "/en"),
}));
