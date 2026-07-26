package SteamGame.recommend.domain.graph.dto;

import java.util.List;

public record NeighborsResponse(long appid, List<GraphNeighbor> neighbors) {}
