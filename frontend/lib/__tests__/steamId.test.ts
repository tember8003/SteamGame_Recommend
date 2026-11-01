import { describe, expect, it } from "vitest";
import { normalizeSteamId } from "../steamId";

describe("normalizeSteamId", () => {
  it("accepts a trimmed SteamID64 or numeric community profile URL", () => {
    expect(normalizeSteamId(" 76561198000000000 ")).toBe("76561198000000000");
    expect(normalizeSteamId("https://steamcommunity.com/profiles/76561198000000000/?l=korean")).toBe("76561198000000000");
  });
  it("rejects display names, vanity URLs, unrelated domains and invalid numbers", () => {
    for (const value of ["", "my_username", "123", "00000000000000000", "https://steamcommunity.com/id/name", "https://evil.com/profiles/76561198000000000", "https://steamcommunity.com/profiles/76561198000000000/extra"]) {
      expect(normalizeSteamId(value)).toBeNull();
    }
  });
});
