import numpy as np
import torch

import tag_embedding.model as model_module
from tag_embedding.graph import build_graph
from tag_embedding.model import BestCheckpoint, auc, concat_ensemble, normalize_rows, train_one


def test_auc_perfect_inverted_and_ties():
    assert auc(np.array([3.0, 4.0]), np.array([1.0, 2.0])) == 1.0
    assert auc(np.array([1.0, 2.0]), np.array([3.0, 4.0])) == 0.0
    assert auc(np.array([1.0, 1.0]), np.array([1.0, 1.0])) == 0.5


def test_concat_ensemble_keeps_each_model_normalized():
    rng = np.random.default_rng(0)
    parts = [normalize_rows(rng.normal(size=(5, 64))) for _ in range(5)]
    out = concat_ensemble(parts)
    assert out.shape == (5, 320)
    for k in range(5):
        assert np.allclose(np.linalg.norm(out[:, k * 64:(k + 1) * 64], axis=1), 1.0)


def two_cluster_graph():
    # 게임 200개를 두 무리로 나누고, 무리마다 태그 10개 중 4개씩 붙인다
    rng = np.random.default_rng(3)
    rows = []
    for game in range(200):
        base = 0 if game < 100 else 10
        for tag in rng.choice(10, size=4, replace=False):
            rows.append((game + 1, base + tag + 1000, int(rng.integers(1, 500))))
    return build_graph(rows)


def test_training_separates_clusters_and_scores_auc():
    g = two_cluster_graph()

    result = train_one(g, seed=42, epochs=100)

    assert result.game_vectors.shape == (200, 64)
    assert np.allclose(np.linalg.norm(result.game_vectors, axis=1), 1.0)
    # 무리 안 태그는 무작위라 음성의 절반(같은 무리 태그)은 구분할 수 없다, 상한이 0.75 다
    assert result.test_auc > 0.7
    sims = result.game_vectors @ result.game_vectors.T
    same = (sims[:100, :100].mean() + sims[100:, 100:].mean()) / 2
    cross = sims[:100, 100:].mean()
    assert same > cross


def test_training_is_reproducible_for_a_seed():
    g = two_cluster_graph()

    a = train_one(g, seed=7, epochs=20)
    b = train_one(g, seed=7, epochs=20)

    assert np.allclose(a.game_vectors, b.game_vectors, atol=1e-5)
    assert a.test_auc == b.test_auc


def test_best_checkpoint_restores_the_best_scoring_weights():
    m = torch.nn.Linear(2, 1)
    best = BestCheckpoint()
    with torch.no_grad():
        m.weight.fill_(1.0)
    best.update(0.8, m, epoch=10)
    with torch.no_grad():
        m.weight.fill_(2.0)
    best.update(0.7, m, epoch=20)

    best.restore(m)

    assert torch.all(m.weight == 1.0)
    assert best.epoch == 10
    assert best.score == 0.8


# 학습과 평가는 학습 간선(시험 10퍼센트, 검증 5퍼센트를 뺀 나머지)으로, 서비스 벡터는 전체 간선으로 만든다
def test_training_uses_train_edges_then_full_graph(monkeypatch):
    calls = []
    real = model_module._tensors

    def spy(n_games, n_tags, src, dst, weights):
        calls.append(len(src))
        return real(n_games, n_tags, src, dst, weights)

    monkeypatch.setattr(model_module, "_tensors", spy)
    g = two_cluster_graph()
    total = len(g.src)

    result = train_one(g, seed=1, epochs=20, eval_every=10)

    assert calls[0] == total - int(round(total * 0.1)) - int(round(total * 0.05))
    assert calls[-1] == total
    assert result.best_epoch in (10, 20)


# 마지막 에폭이 아니라 검증 점수가 가장 높았던 가중치로 시험 점수와 서비스 벡터를 만든다
def test_training_restores_the_best_checkpoint(monkeypatch):
    restored = []
    real_restore = model_module.BestCheckpoint.restore

    def spy(self, model):
        restored.append(self.epoch)
        real_restore(self, model)

    monkeypatch.setattr(model_module.BestCheckpoint, "restore", spy)

    result = train_one(two_cluster_graph(), seed=2, epochs=20, eval_every=10)

    assert restored == [result.best_epoch]
