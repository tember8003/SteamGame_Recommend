export const SITE_URL = "https://playalike.online";
export const SITE_NAME = "Playalike";

// 색인 대상 화면, 메타데이터와 sitemap 이 함께 쓴다, 홈은 빈 경로로 둔다
// /metrics 는 내부 화면이라 넣지 않는다
export const INDEX_ROUTES = {
  home: "",
  steam: "/steam",
  random: "/steam/random",
  input: "/steam/input",
  match: "/steam/match",
  profile: "/steam/profile",
  recent: "/steam/recent",
  similar: "/steam/similar",
  graph: "/steam/graph",
  choice: "/steam/choice",
  reviewGuess: "/steam/review-guess",
} as const;

export type RouteKey = keyof typeof INDEX_ROUTES;
