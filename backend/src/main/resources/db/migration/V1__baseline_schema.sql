CREATE TABLE tags (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE games (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    appid           BIGINT NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    image_url       VARCHAR(255),
    korean_support  BOOLEAN NOT NULL DEFAULT FALSE,
    is_free         BOOLEAN NOT NULL DEFAULT FALSE,
    review_count    INTEGER,
    release_date    DATE,
    owners_estimate BIGINT,
    genres          VARCHAR(500),
    price           INTEGER,
    developer       VARCHAR(255),
    publisher       VARCHAR(255),
    last_crawled_at TIMESTAMPTZ
);

CREATE TABLE reviews (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    appid                 BIGINT NOT NULL UNIQUE,
    total_reviews         INTEGER NOT NULL DEFAULT 0,
    positive_review_text  TEXT,
    negative_review_text  TEXT,
    review_score          VARCHAR(50),
    positive_percentage   DECIMAL(5,2),
    positive_game_hour    DECIMAL(7,2) NOT NULL DEFAULT 0.00,
    negative_game_hour    DECIMAL(7,2) NOT NULL DEFAULT 0.00
);

CREATE TABLE game_tags (
    game_id BIGINT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    tag_id  BIGINT NOT NULL REFERENCES tags(id)  ON DELETE CASCADE,
    votes   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (game_id, tag_id)
);

CREATE TABLE tag_cooccurrence (
    tag1  VARCHAR(255) NOT NULL,
    tag2  VARCHAR(255) NOT NULL,
    count INTEGER NOT NULL,
    PRIMARY KEY (tag1, tag2)
);

CREATE TABLE crawl_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    started_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ,
    batch_type      VARCHAR(50) NOT NULL,
    processed_count INTEGER NOT NULL DEFAULT 0,
    failed_count    INTEGER NOT NULL DEFAULT 0,
    note            TEXT
);

CREATE INDEX idx_games_korean_support  ON games(korean_support);
CREATE INDEX idx_games_is_free         ON games(is_free);
CREATE INDEX idx_games_last_crawled_at ON games(last_crawled_at);
CREATE INDEX idx_reviews_total_reviews ON reviews(total_reviews);
CREATE INDEX idx_game_tags_tag_id      ON game_tags(tag_id);
