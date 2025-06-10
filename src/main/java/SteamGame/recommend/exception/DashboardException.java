package SteamGame.recommend.exception;


public class DashboardException extends RuntimeException {
  private final String steamId;
  private final ErrorType errorType;

  public enum ErrorType {
    STEAM_API_ERROR("Steam API 오류"),
    DATABASE_ERROR("데이터베이스 오류"),
    CACHE_ERROR("캐시 오류"),
    DATA_PROCESSING_ERROR("데이터 처리 오류"),
    UNKNOWN_ERROR("알 수 없는 오류");

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
