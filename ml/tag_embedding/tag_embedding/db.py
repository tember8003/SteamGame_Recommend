import os

import numpy as np
import psycopg

EDGES_SQL = "SELECT game_id, tag_id, votes FROM game_tags"
TAG_NAMES_SQL = "SELECT id, name FROM tags"


def connect() -> psycopg.Connection:
    # 앱과 같은 .env 를 쓰고 호스트와 포트는 compose 가 준다
    return psycopg.connect(
        host=os.environ["DB_HOST"],
        port=os.environ.get("DB_PORT", "5432"),
        dbname=os.environ["DB_NAME"],
        user=os.environ["DB_USER"],
        password=os.environ["DB_PASSWORD"],
        autocommit=True,
    )


def to_vector_literal(v: np.ndarray) -> str:
    return "[" + ",".join(f"{x:.6f}" for x in v) + "]"


class Store:
    def __init__(self, conn: psycopg.Connection):
        self.conn = conn

    def load_edges(self):
        with self.conn.cursor() as cur:
            cur.execute(EDGES_SQL)
            return cur.fetchall()

    def load_tag_names(self) -> dict:
        with self.conn.cursor() as cur:
            cur.execute(TAG_NAMES_SQL)
            return dict(cur.fetchall())

    def start_log(self) -> int:
        with self.conn.cursor() as cur:
            cur.execute(
                "INSERT INTO crawl_log (started_at, batch_type) VALUES (now(), 'tag_embedding') RETURNING id")
            return cur.fetchone()[0]

    def finish_log(self, log_id: int, processed: int, failed: int, note: str) -> None:
        with self.conn.cursor() as cur:
            cur.execute(
                "UPDATE crawl_log SET finished_at = now(), processed_count = %s, failed_count = %s, note = %s "
                "WHERE id = %s",
                (processed, failed, note, log_id))

    def write_vectors(self, game_ids, vectors: np.ndarray):
        # 한 트랜잭션으로 바꾼다, 중간에 실패하면 이전 벡터가 그대로 남는다
        # 이번에 벡터를 만들지 않은 게임은 비운다, 허브 태그만 남아 그래프에서 빠진 게임이다
        with self.conn.transaction(), self.conn.cursor() as cur:
            cur.execute("CREATE TEMP TABLE tmp_tag_embedding (game_id BIGINT PRIMARY KEY, vec TEXT) ON COMMIT DROP")
            with cur.copy("COPY tmp_tag_embedding (game_id, vec) FROM STDIN") as copy:
                for game_id, vector in zip(game_ids, vectors):
                    copy.write_row((int(game_id), to_vector_literal(vector)))
            cur.execute(
                "UPDATE games g SET tag_embedding = t.vec::vector, tag_embedded_at = now() "
                "FROM tmp_tag_embedding t WHERE g.id = t.game_id")
            updated = cur.rowcount
            cur.execute(
                "UPDATE games SET tag_embedding = NULL, tag_embedded_at = NULL "
                "WHERE tag_embedding IS NOT NULL AND id NOT IN (SELECT game_id FROM tmp_tag_embedding)")
            cleared = cur.rowcount
        return updated, cleared
