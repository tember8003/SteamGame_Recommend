package SteamGame.recommend.domain.user.entity;


public enum UserRole {
    ADMIN("ADMIN"),
    USER("USER");

    private final String description;

    UserRole(String description){
        this.description = description;
    }
}
