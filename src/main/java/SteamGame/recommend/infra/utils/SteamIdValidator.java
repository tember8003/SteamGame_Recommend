package SteamGame.recommend.infra.utils;

import lombok.extern.slf4j.Slf4j;

/**
 * Steam ID validation utility
 */
@Slf4j
public class SteamIdValidator {

    private static final long STEAM_ID_MIN_VALUE = 76561197960265728L;
    private static final String STEAM_ID_PATTERN = "\\d{17}";

    private SteamIdValidator() {
    }

    public static void validateSteamId(String steamId) {
        if (steamId == null || steamId.trim().isEmpty()) {
            throw new IllegalArgumentException("Steam ID is required");
        }

        if (!steamId.matches(STEAM_ID_PATTERN)) {
            throw new IllegalArgumentException("Steam ID must be exactly 17 digits");
        }

        try {
            long steamIdLong = Long.parseLong(steamId);
            if (steamIdLong < STEAM_ID_MIN_VALUE) {
                throw new IllegalArgumentException("Invalid Steam ID range");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Steam ID must be numeric");
        }
    }

    public static boolean isValidSteamId(String steamId) {
        try {
            validateSteamId(steamId);
            return true;
        } catch (IllegalArgumentException e) {
            log.debug("Invalid Steam ID: {}, reason: {}", steamId, e.getMessage());
            return false;
        }
    }
}