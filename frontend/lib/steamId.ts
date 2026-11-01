/** Accept only the numeric identifiers supported by the Steam API. */
export function normalizeSteamId(input: string): string | null {
  let value = input.trim();
  if (!/^\d{17}$/.test(value)) {
    try {
      const url = new URL(value);
      if (!["http:", "https:"].includes(url.protocol) || url.hostname !== "steamcommunity.com") return null;
      value = /^\/profiles\/(\d{17})\/?$/.exec(url.pathname)?.[1] ?? "";
    } catch { return null; }
  }
  return /^\d{17}$/.test(value) && value >= "76561197960265728" ? value : null;
}
