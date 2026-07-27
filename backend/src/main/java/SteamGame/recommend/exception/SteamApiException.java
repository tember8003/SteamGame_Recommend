package SteamGame.recommend.exception;

public class SteamApiException extends RuntimeException {
    private final String steamId;
    private final int statusCode;

    public SteamApiException(String message, String steamId) {
        super(message);
        this.steamId = steamId;
        this.statusCode = 0;
    }

    public SteamApiException(String message, String steamId, int statusCode) {
        super(message);
        this.steamId = steamId;
        this.statusCode = statusCode;
    }

    public SteamApiException(String message, String steamId, Throwable cause) {
        super(message, cause);
        this.steamId = steamId;
        this.statusCode = 0;
    }

    public String getSteamId() { return steamId; }
    public int getStatusCode() { return statusCode; }
}