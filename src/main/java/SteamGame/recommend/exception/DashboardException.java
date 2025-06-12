package SteamGame.recommend.exception;


public class DashboardException extends RuntimeException {
  private final String steamId;
  private final ErrorType errorType;

  public enum ErrorType {
    STEAM_API_ERROR("Steam API Error"),
    DATABASE_ERROR("Database Error"),
    CACHE_ERROR("Cache Error"),
    DATA_PROCESSING_ERROR("Data Processing Error"),
    UNKNOWN_ERROR("Unknown Error");

    private final String description;

    ErrorType(String description) {
      this.description = description;
    }

    public String getDescription() { return description; }
  }

  public DashboardException(String message, String steamId, ErrorType errorType) {
    super(message);
    this.steamId = steamId;
    this.errorType = errorType;
  }

  public DashboardException(String message, String steamId, ErrorType errorType, Throwable cause) {
    super(message, cause);
    this.steamId = steamId;
    this.errorType = errorType;
  }

  public String getSteamId() { return steamId; }
  public ErrorType getErrorType() { return errorType; }
}
