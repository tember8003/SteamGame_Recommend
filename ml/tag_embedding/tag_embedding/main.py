import argparse
import sys
import time
from dataclasses import dataclass

from tag_embedding.graph import HUB_SHARE, build_graph, drop_tags, hub_tag_positions
from tag_embedding.model import concat_ensemble, train_one

GATE_SEED = 42


@dataclass
class Config:
    epochs: int = 1000
    seeds: tuple = (42, 43, 44, 45, 46)
    min_auc: float = 0.85
    hub_share: float = HUB_SHARE
    dry_run: bool = False


def run(store, config: Config, train=train_one, log=print) -> int:
    # 성공 0, 품질 관문 실패 1, 예외는 실행 기록을 남기고 다시 던진다
    # dry_run 은 계산만 하고 실행 기록도 남기지 않는다
    log_id = None if config.dry_run else store.start_log()
    started = time.monotonic()
    try:
        graph = build_graph(store.load_edges())
        hubs = hub_tag_positions(graph, config.hub_share)
        names = store.load_tag_names()
        hub_names = ",".join(sorted(names.get(int(t), str(int(t))) for t in graph.tag_ids[hubs]))
        kept = drop_tags(graph, hubs)
        # 남은 게임이 없으면 쓰지 않는다, 빈 목록을 쓰면 모든 게임의 벡터가 비워진다
        if len(kept.game_ids) == 0:
            raise ValueError("no game left after dropping hub tags")

        results = {}
        for seed in config.seeds:
            results[seed] = train(kept, seed, config.epochs)
            log(f"seed={seed} auc={results[seed].test_auc:.4f} bestEpoch={results[seed].best_epoch} "
                f"seconds={int(time.monotonic() - started)}")
        gate_auc = results[GATE_SEED].test_auc
        vectors = concat_ensemble([results[seed].game_vectors for seed in config.seeds])
        note = (f"games={len(kept.game_ids)} tags={len(kept.tag_ids)} hubTags={hub_names} "
                f"auc={gate_auc:.4f} seconds={int(time.monotonic() - started)}")

        if gate_auc < config.min_auc:
            note += f" gate=fail min={config.min_auc}, not written"
            log(note)
            if log_id is not None:
                store.finish_log(log_id, 0, 1, note)
            return 1
        if config.dry_run:
            log(note + " gate=pass dry-run, not written")
            return 0

        updated, cleared = store.write_vectors(kept.game_ids, vectors)
        note += f" cleared={cleared} gate=pass"
        log(note)
        store.finish_log(log_id, updated, 0, note)
        return 0
    except Exception as e:
        if log_id is not None:
            store.finish_log(log_id, 0, 1, f"error={type(e).__name__}: {e}"[:500])
        raise


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="태그 구조 임베딩 학습 배치")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--epochs", type=int, default=1000)
    parser.add_argument("--min-auc", type=float, default=0.85)
    args = parser.parse_args(argv)

    from tag_embedding.db import Store, connect

    config = Config(epochs=args.epochs, min_auc=args.min_auc, dry_run=args.dry_run)
    with connect() as conn:
        return run(Store(conn), config)


if __name__ == "__main__":
    sys.exit(main())
