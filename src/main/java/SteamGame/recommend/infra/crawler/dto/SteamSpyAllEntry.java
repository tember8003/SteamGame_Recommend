package SteamGame.recommend.infra.crawler.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SteamSpyAllEntry {
    private long appid;
    private String name;
    private String developer;
    private String publisher;
    private int positive;
    private int negative;
    private String owners;

    public long getAppid() { return appid; }
    public void setAppid(long appid) { this.appid = appid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDeveloper() { return developer; }
    public void setDeveloper(String developer) { this.developer = developer; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public int getPositive() { return positive; }
    public void setPositive(int positive) { this.positive = positive; }
    public int getNegative() { return negative; }
    public void setNegative(int negative) { this.negative = negative; }
    public String getOwners() { return owners; }
    public void setOwners(String owners) { this.owners = owners; }
}
