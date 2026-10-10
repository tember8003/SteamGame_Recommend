-- 태그 구조로 학습한 게임 벡터(GNN), 시드 모델 5개의 64차원 정규화 벡터를 이어 붙인 320차원
-- 학습 배치(ml/tag_embedding)가 채우고 비슷한 게임 추천이 읽는다
ALTER TABLE games ADD COLUMN tag_embedding   vector(320);
ALTER TABLE games ADD COLUMN tag_embedded_at TIMESTAMPTZ;

CREATE INDEX idx_games_tag_embedding ON games USING hnsw (tag_embedding vector_cosine_ops);

-- 비슷한 게임 추천에서 각 게임을 어느 순위기가 골랐는지, 다른 추천과 이전 행은 null
ALTER TABLE recommendation_impression ADD COLUMN source VARCHAR(10);
ALTER TABLE recommendation_impression ADD CONSTRAINT chk_impression_source
    CHECK (source IS NULL OR source IN ('GEMINI', 'GNN', 'TAG'));
