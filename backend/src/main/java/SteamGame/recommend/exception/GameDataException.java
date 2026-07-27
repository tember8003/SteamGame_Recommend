package SteamGame.recommend.exception;

public class GameDataException extends RuntimeException {
  private final Long appId;

  public GameDataException(String message, Long appId) {
    super(message);
    this.appId = appId;
  }

  public GameDataException(String message, Long appId, Throwable cause) {
    super(message, cause);
    this.appId = appId;
  }

  public Long getAppId() { return appId; }
}