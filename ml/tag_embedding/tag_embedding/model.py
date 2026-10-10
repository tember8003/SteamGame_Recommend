from dataclasses import dataclass

import numpy as np
import torch
from torch import nn
from torch.nn import functional as F
from torch_geometric.nn import GCNConv

from tag_embedding.graph import Graph, edge_index, edge_weight, node_features, split_edges


class Encoder(nn.Module):
    def __init__(self, in_dim: int, hidden: int = 128, out: int = 64, dropout: float = 0.2):
        super().__init__()
        self.conv1 = GCNConv(in_dim, hidden)
        self.conv2 = GCNConv(hidden, out)
        self.dropout = dropout

    def forward(self, x, ei, ew):
        h = F.relu(self.conv1(x, ei, ew))
        h = F.dropout(h, p=self.dropout, training=self.training)
        return self.conv2(h, ei, ew)


@dataclass
class TrainResult:
    game_vectors: np.ndarray  # 게임 번호 순서로 두고 행마다 L2 정규화한다
    test_auc: float
    best_epoch: int


class BestCheckpoint:
    # 검증 점수가 가장 높았던 시점의 가중치를 복사해 둔다, 오래 학습하면 과적합으로 점수가 내려간다

    def __init__(self):
        self.score = float("-inf")
        self.epoch = 0
        self.state = None

    def update(self, score: float, model: nn.Module, epoch: int) -> None:
        if score > self.score:
            self.score = score
            self.epoch = epoch
            self.state = {k: v.detach().clone() for k, v in model.state_dict().items()}

    def restore(self, model: nn.Module) -> None:
        model.load_state_dict(self.state)


def auc(pos: np.ndarray, neg: np.ndarray) -> float:
    # 양성이 음성보다 높게 나올 확률, 같은 점수는 평균 순위로 반씩 센다
    scores = np.concatenate([pos, neg])
    order = scores.argsort(kind="mergesort")
    ranks = np.empty(len(scores), dtype=np.float64)
    ranks[order] = np.arange(1, len(scores) + 1)
    _, inverse, counts = np.unique(scores, return_inverse=True, return_counts=True)
    ranks = (np.bincount(inverse, weights=ranks) / counts)[inverse]
    n_pos, n_neg = len(pos), len(neg)
    return float((ranks[:n_pos].sum() - n_pos * (n_pos + 1) / 2) / (n_pos * n_neg))


def normalize_rows(v: np.ndarray) -> np.ndarray:
    norms = np.linalg.norm(v, axis=1, keepdims=True)
    return v / np.clip(norms, 1e-12, None)


def concat_ensemble(vectors_list) -> np.ndarray:
    # 모델마다 축 방향이 달라 평균 내지 않고 이어 붙인다, 내적이 모델별 코사인의 합이 된다
    return np.concatenate([normalize_rows(v) for v in vectors_list], axis=1).astype(np.float32)


def _tensors(n_games, n_tags, src, dst, weights):
    x = torch.from_numpy(node_features(n_games, n_tags, src, dst))
    ei = torch.from_numpy(edge_index(n_games, src, dst))
    ew = torch.from_numpy(np.concatenate([weights, weights]))
    return x, ei, ew


def _link_scores(z: np.ndarray, games: np.ndarray, tags: np.ndarray) -> np.ndarray:
    return (z[games] * z[tags]).sum(axis=1)


def train_one(graph: Graph, seed: int, epochs: int, lr: float = 0.01, test_frac: float = 0.1,
              val_frac: float = 0.05, eval_every: int = 50) -> TrainResult:
    # 학습과 평가는 학습 간선만으로 하고, 서비스 벡터는 학습한 가중치로 전체 간선에서 뽑는다
    # 검증 점수가 가장 높았던 시점의 가중치를 쓴다, 실험도 이렇게 골랐다
    torch.manual_seed(seed)
    rng = np.random.default_rng(seed)
    n_games, n_tags = len(graph.game_ids), len(graph.tag_ids)
    weights = edge_weight(graph.votes)
    train_idx, test_idx = split_edges(len(graph.src), rng, test_frac)
    n_val = int(round(len(graph.src) * val_frac))
    val_idx, train_idx = train_idx[:n_val], train_idx[n_val:]
    val_game = graph.src[val_idx]
    val_tag = graph.dst[val_idx] + n_games
    val_neg_game = rng.integers(0, n_games, len(val_idx))
    val_neg_tag = rng.integers(n_games, n_games + n_tags, len(val_idx))

    x, ei, ew = _tensors(n_games, n_tags, graph.src[train_idx], graph.dst[train_idx], weights[train_idx])
    pos_game = torch.from_numpy(graph.src[train_idx])
    pos_tag = torch.from_numpy(graph.dst[train_idx] + n_games)
    ones = torch.ones(len(train_idx))
    zeros = torch.zeros(len(train_idx))

    model = Encoder(n_tags)
    optimizer = torch.optim.Adam(model.parameters(), lr=lr)
    best = BestCheckpoint()
    for epoch in range(1, epochs + 1):
        model.train()
        optimizer.zero_grad()
        z = model(x, ei, ew)
        neg_game = torch.randint(0, n_games, (len(train_idx),))
        neg_tag = torch.randint(n_games, n_games + n_tags, (len(train_idx),))
        pos = (z[pos_game] * z[pos_tag]).sum(dim=1)
        neg = (z[neg_game] * z[neg_tag]).sum(dim=1)
        loss = F.binary_cross_entropy_with_logits(pos, ones) + F.binary_cross_entropy_with_logits(neg, zeros)
        loss.backward()
        optimizer.step()

        if epoch % eval_every == 0 or epoch == epochs:
            model.eval()
            with torch.no_grad():
                z = model(x, ei, ew).numpy()
            best.update(auc(_link_scores(z, val_game, val_tag), _link_scores(z, val_neg_game, val_neg_tag)),
                        model, epoch)

    best.restore(model)
    model.eval()
    with torch.no_grad():
        z = model(x, ei, ew).numpy()
        test_game = graph.src[test_idx]
        test_tag = graph.dst[test_idx] + n_games
        neg_game = rng.integers(0, n_games, len(test_idx))
        neg_tag = rng.integers(n_games, n_games + n_tags, len(test_idx))
        test_auc = auc(_link_scores(z, test_game, test_tag), _link_scores(z, neg_game, neg_tag))

        full_x, full_ei, full_ew = _tensors(n_games, n_tags, graph.src, graph.dst, weights)
        game_vectors = model(full_x, full_ei, full_ew)[:n_games].numpy()

    return TrainResult(normalize_rows(game_vectors).astype(np.float32), test_auc, best.epoch)
