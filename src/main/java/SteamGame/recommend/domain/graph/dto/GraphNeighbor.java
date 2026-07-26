package SteamGame.recommend.domain.graph.dto;

public record GraphNeighbor(long appid, String name, String image, String genres, double similarity) {}
