import numpy as np

from tag_embedding.graph import (
    build_graph, drop_tags, edge_index, edge_weight, hub_tag_positions, node_features, split_edges,
)


def sample_graph():
    # 게임 10개, 태그 3개
    # 태그 100 은 게임 3개(30퍼센트), 태그 200 은 게임 2개, 태그 300 은 게임 10개 전부
    rows = [(g, 300, 5) for g in range(1, 11)]
    rows += [(1, 100, 9), (2, 100, 9), (3, 100, 9)]
    rows += [(4, 200, 1), (5, 200, 1)]
    return build_graph(rows)


def test_build_graph_maps_ids_to_positions():
    g = sample_graph()
    assert list(g.game_ids) == list(range(1, 11))
    assert list(g.tag_ids) == [100, 200, 300]
    assert len(g.src) == len(g.dst) == len(g.votes) == 15


def test_hub_tags_include_the_thirty_percent_boundary():
    g = sample_graph()
    hubs = set(g.tag_ids[hub_tag_positions(g, 0.30)])
    assert hubs == {100, 300}


def test_drop_tags_removes_games_left_without_tags():
    g = sample_graph()
    kept = drop_tags(g, hub_tag_positions(g, 0.30))
    assert list(kept.tag_ids) == [200]
    assert list(kept.game_ids) == [4, 5]


def test_split_edges_is_disjoint_and_covers_all():
    train, test = split_edges(100, np.random.default_rng(1), 0.1)
    assert len(test) == 10
    assert len(train) == 90
    assert set(train).isdisjoint(test)
    assert set(train) | set(test) == set(range(100))


def test_features_use_only_given_edges():
    # 게임 0 은 태그 0, 1 에 붙어 있지만 태그 1 간선은 시험용이라 특성에 넣지 않는다
    x = node_features(2, 2, np.array([0, 1]), np.array([0, 1]))
    assert x.shape == (4, 2)
    assert x[0].tolist() == [1, 0]
    assert x[1].tolist() == [0, 1]
    # 태그 노드는 자기 칸만 1
    assert x[2].tolist() == [1, 0]
    assert x[3].tolist() == [0, 1]


def test_edge_index_is_undirected_with_tag_offset():
    ei = edge_index(3, np.array([0, 2]), np.array([1, 0]))
    assert ei.tolist() == [[0, 2, 4, 3], [4, 3, 0, 2]]


def test_edge_weight_is_log_scaled_to_max_one():
    w = edge_weight(np.array([0, 9, 99]))
    assert w[0] == 0
    assert np.isclose(w[2], 1.0)
    assert np.isclose(w[1], np.log1p(9) / np.log1p(99))
