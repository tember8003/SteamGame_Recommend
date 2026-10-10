from dataclasses import dataclass

import numpy as np

# 게임의 이 비율 이상에 붙은 태그는 무관한 게임끼리 이어 버려서 뺀다
HUB_SHARE = 0.30


@dataclass
class Graph:
    game_ids: np.ndarray  # games.id, 배열 위치를 게임 번호로 쓴다
    tag_ids: np.ndarray   # tags.id, 배열 위치를 태그 번호로 쓴다
    src: np.ndarray       # 간선마다 게임 번호를 담는다
    dst: np.ndarray       # 간선마다 태그 번호를 담는다
    votes: np.ndarray


def build_graph(rows) -> Graph:
    # rows 는 (game_id, tag_id, votes) 묶음, 같은 쌍은 한 번만 온다
    arr = np.asarray(list(rows), dtype=np.int64).reshape(-1, 3)
    game_ids, src = np.unique(arr[:, 0], return_inverse=True)
    tag_ids, dst = np.unique(arr[:, 1], return_inverse=True)
    return Graph(game_ids, tag_ids, src, dst, arr[:, 2])


def hub_tag_positions(graph: Graph, share: float = HUB_SHARE) -> np.ndarray:
    # 그래프에 있는 게임 수를 분모로 쓴다, 경계값은 허브로 본다
    counts = np.bincount(graph.dst, minlength=len(graph.tag_ids))
    return np.where(counts >= share * len(graph.game_ids))[0]


def drop_tags(graph: Graph, tag_positions) -> Graph:
    # 태그를 빼고 다시 만든다, 남은 태그가 없는 게임은 그래프에서 빠진다
    keep = ~np.isin(graph.dst, tag_positions)
    rows = np.stack([graph.game_ids[graph.src[keep]], graph.tag_ids[graph.dst[keep]], graph.votes[keep]], axis=1)
    return build_graph(rows)


def split_edges(n_edges: int, rng: np.random.Generator, test_frac: float = 0.1):
    perm = rng.permutation(n_edges)
    n_test = int(round(n_edges * test_frac))
    return perm[n_test:], perm[:n_test]


def node_features(n_games: int, n_tags: int, src: np.ndarray, dst: np.ndarray) -> np.ndarray:
    # 게임은 주어진 간선의 태그 칸만 1 로, 태그는 자기 칸만 1 로 채운다
    # 평가할 때는 학습 간선만 넘겨야 시험 간선의 답이 특성으로 새지 않는다
    x = np.zeros((n_games + n_tags, n_tags), dtype=np.float32)
    x[src, dst] = 1.0
    x[n_games + np.arange(n_tags), np.arange(n_tags)] = 1.0
    return x


def edge_index(n_games: int, src: np.ndarray, dst: np.ndarray) -> np.ndarray:
    # 태그 노드 번호는 게임 뒤에 이어 붙인다, GCN 은 양방향 간선이 필요하다
    tags = dst + n_games
    return np.stack([np.concatenate([src, tags]), np.concatenate([tags, src])]).astype(np.int64)


def edge_weight(votes: np.ndarray) -> np.ndarray:
    # 표 수는 인기작이 그래프를 지배하지 않게 로그로 줄이고 최대값을 1 로 맞춘다
    logged = np.log1p(votes.astype(np.float64))
    top = logged.max()
    return (logged / top if top > 0 else logged).astype(np.float32)
