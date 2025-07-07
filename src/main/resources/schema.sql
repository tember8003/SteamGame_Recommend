-- ============================================
-- Steam Game Recommend - H2 Database
-- Selected 10,000 High-Quality Games
-- Generated: 2025-07-03 17:03:32
-- ============================================

-- H2 설정
SET MODE MySQL;
SET FOREIGN_KEY_CHECKS FALSE;

-- ============================================
-- 테이블 생성
-- ============================================

CREATE TABLE IF NOT EXISTS games (
    id BIGINT NOT NULL AUTO_INCREMENT,
    appid BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    image_url VARCHAR(255) DEFAULT NULL,
    korean_support BOOLEAN DEFAULT FALSE,
    is_free BOOLEAN NOT NULL DEFAULT FALSE,
    review_count INT DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE (appid)
    );

CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    appid BIGINT NOT NULL,
    total_reviews INT DEFAULT 0,
    positive_review_text TEXT,
    negative_review_text TEXT,
    review_score VARCHAR(50) DEFAULT NULL,
    positive_percentage DECIMAL(5,2) DEFAULT NULL,
    positive_game_hour DECIMAL(7,2) DEFAULT 0.00,
    negative_game_hour DECIMAL(7,2) DEFAULT 0.00,
    PRIMARY KEY (id),
    UNIQUE (appid),
    CONSTRAINT fk_reviews_game FOREIGN KEY (appid) REFERENCES games (appid) ON DELETE CASCADE
    );


CREATE TABLE IF NOT EXISTS tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (name)
    );

CREATE TABLE IF NOT EXISTS game_tags (
    game_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (game_id, tag_id),
    CONSTRAINT fk_game FOREIGN KEY (game_id) REFERENCES games (id) ON DELETE CASCADE,
    CONSTRAINT fk_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS tag_cooccurrence (
    tag1 VARCHAR(255) NOT NULL,
    tag2 VARCHAR(255) NOT NULL,
    count INT NOT NULL,
    PRIMARY KEY (tag1, tag2)
);

-- ============================================
-- 인덱스 생성
-- ============================================

CREATE INDEX IF NOT EXISTS idx_games_appid ON games(appid);
CREATE INDEX IF NOT EXISTS idx_games_name ON games(name);
CREATE INDEX IF NOT EXISTS idx_games_review_count ON games(review_count);

-- reviews 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_reviews_appid ON reviews(appid);

-- game_tags 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_game_tags_game_id ON game_tags(game_id);
CREATE INDEX IF NOT EXISTS idx_game_tags_tag_id ON game_tags(tag_id);

-- tags 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_tags_name ON tags(name);

-- tag_cooccurrence 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_tag_cooccurrence_tag1 ON tag_cooccurrence(tag1);
CREATE INDEX IF NOT EXISTS idx_tag_cooccurrence_tag2 ON tag_cooccurrence(tag2);
