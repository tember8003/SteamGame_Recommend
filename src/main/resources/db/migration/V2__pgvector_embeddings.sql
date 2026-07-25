-- pgvector 확장 등록 + games 임베딩 컬럼 추가
-- vector(768) 차원은 app.embedding.dimension 과 반드시 일치
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE games ADD COLUMN embedding            vector(768);
ALTER TABLE games ADD COLUMN embedding_updated_at TIMESTAMPTZ;

-- 코사인 거리 최근접 검색용 HNSW 인덱스 (pgvector HNSW 는 2000 차원까지만 지원)
CREATE INDEX idx_games_embedding_hnsw ON games USING hnsw (embedding vector_cosine_ops);
