import numpy as np
import pytest

from tag_embedding.db import to_vector_literal
from tag_embedding.main import Config, run
from tag_embedding.model import TrainResult


class FakeStore:
    def __init__(self, rows):
        self.rows = rows
        self.logs = []
        self.finished = []
        self.written = None

    def load_edges(self):
        return self.rows

    def load_tag_names(self):
        return {100: "Singleplayer", 200: "Metroidvania", 300: "Souls-like"}

    def start_log(self):
        self.logs.append("started")
        return 1

    def finish_log(self, log_id, processed, failed, note):
        self.finished.append((log_id, processed, failed, note))

    def write_vectors(self, game_ids, vectors):
        self.written = (list(game_ids), vectors)
        return len(game_ids), 3


# 게임 10개, 태그 100 은 모든 게임에 붙어 허브로 빠진다(태그 200 은 20퍼센트, 300 은 10퍼센트라 남는다)
# 게임 4 부터 10 은 태그 100 만 있어서 그래프에서 빠진다
ROWS = [(g, 100, 9) for g in range(1, 11)] + [(1, 200, 5), (2, 200, 5), (3, 300, 5)]


def fake_train(auc_value):
    def train(graph, seed, epochs):
        vectors = np.ones((len(graph.game_ids), 64), dtype=np.float32) / 8.0
        return TrainResult(vectors, auc_value if seed == 42 else 0.5, epochs)
    return train


def test_vector_literal_format():
    assert to_vector_literal(np.array([0.5, -0.25])) == "[0.500000,-0.250000]"


def test_writes_vectors_and_clears_games_without_vector():
    store = FakeStore(ROWS)

    code = run(store, Config(epochs=1), train=fake_train(0.9))

    assert code == 0
    game_ids, vectors = store.written
    assert game_ids == [1, 2, 3]
    assert vectors.shape == (3, 320)
    log_id, processed, failed, note = store.finished[0]
    assert (processed, failed) == (3, 0)
    assert "auc=0.9000" in note and "hubTags=Singleplayer" in note and "cleared=3" in note and "gate=pass" in note


def test_gate_failure_skips_write():
    store = FakeStore(ROWS)

    code = run(store, Config(epochs=1), train=fake_train(0.6))

    assert code == 1
    assert store.written is None
    _, processed, failed, note = store.finished[0]
    assert (processed, failed) == (0, 1)
    assert "gate=fail" in note


def test_dry_run_writes_nothing():
    store = FakeStore(ROWS)

    code = run(store, Config(epochs=1, dry_run=True), train=fake_train(0.9))

    assert code == 0
    assert store.written is None
    assert store.logs == []
    assert store.finished == []


# 허브 태그만 있는 게임들이면 남는 게임이 없다, 빈 목록을 쓰면 모든 게임의 벡터가 비워진다
def test_refuses_to_write_when_no_game_is_left():
    store = FakeStore([(g, 100, 9) for g in range(1, 6)])

    with pytest.raises(ValueError):
        run(store, Config(epochs=1), train=fake_train(0.9))

    assert store.written is None
    _, processed, failed, note = store.finished[0]
    assert (processed, failed) == (0, 1)
    assert "ValueError" in note


# 몇 시간 걸리는 배치라 시드마다 진행을 남긴다
def test_logs_progress_per_seed():
    store = FakeStore(ROWS)
    lines = []

    run(store, Config(epochs=1), train=fake_train(0.9), log=lines.append)

    seed_lines = [line for line in lines if line.startswith("seed=")]
    assert [line.split()[0] for line in seed_lines] == ["seed=42", "seed=43", "seed=44", "seed=45", "seed=46"]


def test_main_parses_options_and_returns_run_code(monkeypatch):
    import tag_embedding.db as db
    import tag_embedding.main as main_module

    class Conn:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

    seen = {}

    def fake_run(store, config):
        seen["config"] = config
        return 1

    monkeypatch.setattr(db, "connect", lambda: Conn())
    monkeypatch.setattr(db, "Store", lambda conn: "store")
    monkeypatch.setattr(main_module, "run", fake_run)

    code = main_module.main(["--dry-run", "--epochs", "5", "--min-auc", "0.9"])

    assert code == 1
    assert seen["config"].dry_run is True
    assert seen["config"].epochs == 5
    assert seen["config"].min_auc == 0.9


def test_error_is_recorded_and_reraised():
    store = FakeStore(ROWS)

    def broken(graph, seed, epochs):
        raise MemoryError("out of memory")

    with pytest.raises(MemoryError):
        run(store, Config(epochs=1), train=broken)

    _, processed, failed, note = store.finished[0]
    assert (processed, failed) == (0, 1)
    assert "MemoryError" in note
    assert store.written is None
